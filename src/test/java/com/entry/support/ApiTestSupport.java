package com.entry.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Small helpers to set up entrances and members through the real API. */
public final class ApiTestSupport {

    private final MockMvc mvc;

    public ApiTestSupport(MockMvc mvc) {
        this.mvc = mvc;
    }

    public record Member(String id, String token, String entranceId) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Registers a user who creates a new entrance (ACTIVE admin). */
    public Member newAdmin(String firstName) throws Exception {
        MvcResult r = register(firstName, "\"newEntrance\": {\"name\": \"Entrance " + UUID.randomUUID()
                + "\", \"address\": \"Test Street 1\"}");
        return member(r);
    }

    /** Registers with the admin's invite code and gets approved. */
    public Member newResident(Member admin, String firstName) throws Exception {
        String inviteCode = JsonPath.read(mvc.perform(get("/api/entrances/current")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.inviteCode");
        Member pending = member(register(firstName, "\"inviteCode\": \"" + inviteCode + "\""));
        mvc.perform(post("/api/admin/members/{id}/approve", pending.id()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        return pending;
    }

    public static String json(MvcResult r, String path) throws Exception {
        Object value = JsonPath.read(r.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }

    private MvcResult register(String firstName, String entranceJson) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s@example.com", "password": "password123", "firstName": "%s",
                         "lastName": "Tester", "floor": "3", "apartment": "9", %s}"""
                        .formatted(UUID.randomUUID(), firstName, entranceJson)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static Member member(MvcResult r) throws Exception {
        return new Member(json(r, "$.user.id"), json(r, "$.accessToken"), json(r, "$.user.entrance.id"));
    }
}
