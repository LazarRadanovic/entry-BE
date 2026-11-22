package com.entry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.entry.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end: create entrance, join with invite code, approval, entrance isolation, refresh rotation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void fullMembershipFlow() throws Exception {
        // 1. Admin creates an entrance
        MvcResult adminReg = register(uniqueEmail(), """
                "newEntrance": {"name": "Entrance 3", "address": "Belgrade Street 12"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ENTRANCE_ADMIN"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andReturn();
        String adminToken = token(adminReg);

        String inviteCode = JsonPath.read(mvc.perform(get("/api/entrances/current").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.inviteCode");
        assertThat(inviteCode).hasSize(8);

        // 2. Resident joins with the code (lower-case works) and is PENDING
        MvcResult residentReg = register(uniqueEmail(), "\"inviteCode\": \"" + inviteCode.toLowerCase() + "\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.status").value("PENDING"))
                .andReturn();
        String residentToken = token(residentReg);
        String residentId = JsonPath.read(residentReg.getResponse().getContentAsString(), "$.user.id");

        // 3. Pending: can see own profile, cannot see residents
        mvc.perform(get("/api/users/me").header("Authorization", bearer(residentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entrance.name").value("Entrance 3"));
        mvc.perform(get("/api/users").header("Authorization", bearer(residentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_NOT_ACTIVE"));

        // 4. Admin of ANOTHER entrance cannot approve them
        String otherAdminToken = token(register(uniqueEmail(), """
                "newEntrance": {"name": "Entrance 1", "address": "Other Street 5"}""").andReturn());
        mvc.perform(post("/api/admin/members/{id}/approve", residentId).header("Authorization", bearer(otherAdminToken)))
                .andExpect(status().isNotFound());

        // 5. Resident cannot use admin endpoints
        mvc.perform(get("/api/admin/members").header("Authorization", bearer(residentToken)))
                .andExpect(status().isForbidden());

        // 6. Own admin approves -> takes effect immediately with the same access token
        mvc.perform(get("/api/admin/members?status=PENDING").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/admin/members/{id}/approve", residentId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/users").header("Authorization", bearer(residentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesAllSessions() throws Exception {
        MvcResult reg = register(uniqueEmail(), """
                "newEntrance": {"name": "Entrance 7", "address": "Some Street 1"}""").andReturn();
        Cookie first = reg.getResponse().getCookie("refresh_token");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reusing the rotated token is rejected and kills the newer session too
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void validationAndCredentialErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "firstName": "", "lastName": "X",
                         "floor": "1", "apartment": "2"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.entranceChoiceValid").exists());

        register(uniqueEmail(), "\"inviteCode\": \"NOPE1234\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\", \"password\": \"whatever123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String entranceJson) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "password123", "firstName": "Ana", "lastName": "Petrovic",
                 "floor": "2", "apartment": "7", %s}""".formatted(email, entranceJson)));
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
