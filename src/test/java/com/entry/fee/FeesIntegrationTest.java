package com.entry.fee;

import static com.entry.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.entry.TestcontainersConfiguration;
import com.entry.support.ApiTestSupport;
import com.entry.support.ApiTestSupport.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FeesIntegrationTest {

    @Autowired
    MockMvc mvc;

    Member admin;
    Member resident;
    Member outsider;

    @BeforeEach
    void setUp() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        admin = api.newAdmin("Ana");        // floor 3, apt 9 (ApiTestSupport default)
        resident = api.newResident(admin, "Marko"); // same apartment as Ana
        outsider = api.newAdmin("Zoran");
    }

    @Test
    void adminMarksPaymentsEveryoneSeesTheTable() throws Exception {
        // one shared apartment (both live in 3/9) + an apartment without app users
        mvc.perform(post("/api/admin/fees/units").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"floor\": \"1\", \"apartment\": \"2\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/fees/settings").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 500, \"currency\": \"RSD\"}"))
                .andExpect(status().isNoContent());

        MvcResult table = mvc.perform(get("/api/fees?year=2026").header("Authorization", resident.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500))
                .andExpect(jsonPath("$.currency").value("RSD"))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].floor").value("1"))   // sorted by floor
                .andExpect(jsonPath("$.rows[0].residents.length()").value(0))
                .andExpect(jsonPath("$.rows[1].residents.length()").value(2))
                .andExpect(jsonPath("$.rows[1].mine").value(true))
                .andExpect(jsonPath("$.rows[1].paid.length()").value(12))
                .andReturn();
        String myUnit = json(table, "$.rows[1].unitId");

        // admin marks March paid (idempotent), then unmarks it
        String body = "{\"unitId\": \"" + myUnit + "\", \"year\": 2026, \"month\": 3, \"paid\": %s}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/admin/fees/payments").header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body.formatted("true")))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/fees?year=2026").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.rows[1].paid[2]").isNotEmpty())
                .andExpect(jsonPath("$.rows[1].paid[3]").doesNotExist());
        mvc.perform(get("/api/fees?year=2025").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.rows[1].paid[2]").doesNotExist());

        mvc.perform(put("/api/admin/fees/payments").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("false")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/fees?year=2026").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.rows[1].paid[2]").doesNotExist());

        // residents and other entrances can't change anything
        mvc.perform(put("/api/admin/fees/payments").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("true")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/fees/payments").header("Authorization", outsider.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("true")))
                .andExpect(status().isNotFound());

        // an occupied apartment can't be removed, an empty one can
        mvc.perform(delete("/api/admin/fees/units/{id}", myUnit).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNIT_OCCUPIED"));
        String emptyUnit = json(mvc.perform(get("/api/fees").header("Authorization", admin.bearer())).andReturn(),
                "$.rows[0].unitId");
        mvc.perform(delete("/api/admin/fees/units/{id}", emptyUnit).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/admin/fees/units").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"floor\": \"3\", \"apartment\": \"9\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void naturalSortPutsNumbersFirst() {
        assertThat(FeeService.natural("2", "10")).isNegative();
        assertThat(FeeService.natural("10", "P")).isNegative();
        assertThat(FeeService.natural("a", "B")).isNegative();
    }
}
