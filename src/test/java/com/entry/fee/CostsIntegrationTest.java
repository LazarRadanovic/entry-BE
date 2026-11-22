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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CostsIntegrationTest {

    @Autowired
    MockMvc mvc;

    Member admin;
    Member resident;
    Member outsider;

    @BeforeEach
    void setUp() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        admin = api.newAdmin("Ana");                // floor 3, apt 9 (ApiTestSupport default)
        resident = api.newResident(admin, "Marko"); // same apartment as Ana
        outsider = api.newAdmin("Zoran");
    }

    @Test
    void costsAssignedToApartmentsWithPaymentsAndAreaSplit() throws Exception {
        // an apartment without app users (40 m2) + the residents' apartment (set to 60 m2)
        send(post("/api/admin/units"), admin, "{\"floor\": \"1\", \"apartment\": \"2\", \"areaM2\": 40}")
                .andExpect(status().isNoContent());
        var units = send(get("/api/units"), resident, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].floor").value("1"))        // sorted by floor
                .andExpect(jsonPath("$[0].residents.length()").value(0))
                .andExpect(jsonPath("$[1].residents.length()").value(2))
                .andExpect(jsonPath("$[1].mine").value(true))
                .andReturn();
        String otherUnit = json(units, "$[0].id");
        String myUnit = json(units, "$[1].id");
        send(put("/api/admin/units/{id}", myUnit), admin, "{\"areaM2\": 60}").andExpect(status().isNoContent());

        String cleaning = json(send(post("/api/admin/costs"), admin, cost("Čišćenje zgrade", "PER_APARTMENT", 500,
                "MONTHLY", true, "")).andExpect(status().isCreated()).andReturn(), "$.id");
        String garage = json(send(post("/api/admin/costs"), admin, cost("Garaža", "PER_APARTMENT", 300,
                "MONTHLY", false, "\"" + otherUnit + "\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitIds.length()").value(1)).andReturn(), "$.id");
        String roof = json(send(post("/api/admin/costs"), admin, cost("Krov", "BY_AREA", 1000, "ONE_TIME", true, ""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.missingArea").value(false)).andReturn(), "$.id");

        send(get("/api/costs"), resident, null).andExpect(jsonPath("$.length()").value(3));

        // area split: 400 / 600; one-time costs have a single payment slot
        send(get("/api/costs/{id}/table", roof), resident, null)
                .andExpect(jsonPath("$.rows[0].share").value(400.0))
                .andExpect(jsonPath("$.rows[1].share").value(600.0))
                .andExpect(jsonPath("$.rows[1].paid.length()").value(1));

        // the garage cost only shows the garage apartment, and isn't among my costs
        send(get("/api/costs/{id}/table?year=2026", garage), resident, null)
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].unitId").value(otherUnit));
        send(get("/api/costs/mine?year=2026"), resident, null)
                .andExpect(jsonPath("$.unitId").value(myUnit))
                .andExpect(jsonPath("$.costs.length()").value(2))
                .andExpect(jsonPath("$.costs[0].cost.name").value("Čišćenje zgrade"))
                .andExpect(jsonPath("$.costs[0].share").value(500.0))
                .andExpect(jsonPath("$.costs[0].paid.length()").value(12))
                .andExpect(jsonPath("$.costs[1].share").value(600.0));

        // admin marks March paid (idempotent); other years are unaffected
        String march = payment(cleaning, myUnit, "2026-03", true);
        for (int i = 0; i < 2; i++) {
            send(put("/api/admin/costs/payments"), admin, march).andExpect(status().isNoContent());
        }
        send(get("/api/costs/mine?year=2026"), resident, null)
                .andExpect(jsonPath("$.costs[0].paid[2]").isNotEmpty())
                .andExpect(jsonPath("$.costs[0].paid[3]").doesNotExist());
        send(get("/api/costs/{id}/table?year=2025", cleaning), resident, null)
                .andExpect(jsonPath("$.rows[1].paid[2]").doesNotExist());
        send(put("/api/admin/costs/payments"), admin, payment(roof, myUnit, "ONCE", true))
                .andExpect(status().isNoContent());
        send(get("/api/costs/{id}/table", roof), resident, null).andExpect(jsonPath("$.rows[1].paid[0]").isNotEmpty());

        // wrong period kind, unassigned apartment, residents and other entrances
        send(put("/api/admin/costs/payments"), admin, payment(roof, myUnit, "2026-03", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        send(put("/api/admin/costs/payments"), admin, payment(garage, myUnit, "2026-03", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNIT_NOT_ASSIGNED"));
        send(put("/api/admin/costs/payments"), resident, march).andExpect(status().isForbidden());
        send(put("/api/admin/costs/payments"), outsider, march).andExpect(status().isNotFound());
        send(post("/api/admin/costs"), outsider, cost("X", "PER_APARTMENT", 1, "MONTHLY", false,
                "\"" + otherUnit + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UNIT"));
        send(get("/api/costs/{id}/table", cleaning), outsider, null).andExpect(status().isNotFound());

        // assign the garage to both apartments: now it's mine too
        send(put("/api/admin/costs/{id}/units", garage), admin,
                "{\"appliesToAll\": false, \"unitIds\": [\"" + otherUnit + "\", \"" + myUnit + "\"]}")
                .andExpect(status().isOk());
        send(get("/api/costs/mine"), resident, null).andExpect(jsonPath("$.costs.length()").value(3));

        // an apartment without area makes the area split impossible until it's filled in
        send(post("/api/admin/units"), admin, "{\"floor\": \"0\", \"apartment\": \"1\"}")
                .andExpect(status().isNoContent());
        send(get("/api/costs/{id}/table", roof), resident, null)
                .andExpect(jsonPath("$.cost.missingArea").value(true))
                .andExpect(jsonPath("$.rows[0].share").doesNotExist());

        // unpaid costs are deleted, paid ones archived; both disappear from the list
        send(delete("/api/admin/costs/{id}", garage), admin, null).andExpect(status().isNoContent());
        send(delete("/api/admin/costs/{id}", cleaning), admin, null).andExpect(status().isNoContent());
        send(get("/api/costs"), resident, null).andExpect(jsonPath("$.length()").value(1));
        send(get("/api/costs/{id}/table", cleaning), resident, null).andExpect(status().isNotFound());

        // an occupied apartment can't be removed, an empty one can
        send(delete("/api/admin/units/{id}", myUnit), admin, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UNIT_OCCUPIED"));
        send(delete("/api/admin/units/{id}", otherUnit), admin, null).andExpect(status().isNoContent());
        send(post("/api/admin/units"), admin, "{\"floor\": \"3\", \"apartment\": \"9\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void subsetCostNeedsApartments() throws Exception {
        send(post("/api/admin/costs"), admin, cost("Garaža", "PER_APARTMENT", 300, "MONTHLY", false, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NO_UNITS"));
    }

    @Test
    void naturalSortPutsNumbersFirst() {
        assertThat(CostService.natural("2", "10")).isNegative();
        assertThat(CostService.natural("10", "P")).isNegative();
        assertThat(CostService.natural("a", "B")).isNegative();
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Member who, String body) throws Exception {
        req.header("Authorization", who.bearer());
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private static String cost(String name, String type, int amount, String recurrence, boolean all, String unitIds) {
        return """
                {"name": "%s", "type": "%s", "amount": %d, "currency": "RSD", "recurrence": "%s",
                 "appliesToAll": %s, "unitIds": [%s]}""".formatted(name, type, amount, recurrence, all, unitIds);
    }

    private static String payment(String costId, String unitId, String period, boolean paid) {
        return "{\"costId\": \"%s\", \"unitId\": \"%s\", \"period\": \"%s\", \"paid\": %s}"
                .formatted(costId, unitId, period, paid);
    }
}
