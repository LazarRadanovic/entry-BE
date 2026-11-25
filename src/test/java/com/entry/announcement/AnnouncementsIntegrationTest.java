package com.entry.announcement;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
class AnnouncementsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void residentsAnnounceNoiseAndNeighboursAreNotified() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        Member admin = api.newAdmin("Ana");
        Member marko = api.newResident(admin, "Marko");
        Member jelena = api.newResident(admin, "Jelena");
        Member outsider = api.newAdmin("Zoran");
        LocalDate today = AnnouncementService.today();

        String renovation = json(send(post("/api/announcements"), marko,
                body("RENOVATION", today, today.plusDays(4), "08:00", "16:00", "Menjamo pločice u kupatilu"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.apartment").value("9"))
                .andExpect(jsonPath("$.fromTime").value("08:00:00"))
                .andExpect(jsonPath("$.ongoing").value(true))
                .andReturn(), "$.id");
        String party = json(send(post("/api/announcements"), marko,
                body("CELEBRATION", today.plusDays(10), today.plusDays(10), "20:00", "01:00", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ongoing").value(false))
                .andReturn(), "$.id");

        // neighbours get a notification, the author doesn't
        // (fan-out is asynchronous; Jelena also has MEMBERSHIP_APPROVED)
        long count = 0;
        for (int i = 0; i < 50 && count < 3; i++) {
            Thread.sleep(100);
            count = Long.parseLong(json(send(get("/api/notifications/unread-count"), jelena, null).andReturn(),
                    "$.count"));
        }
        assertThat(count).isEqualTo(3);
        String day = DateTimeFormatter.ofPattern("d.M.").format(today.plusDays(10));
        send(get("/api/notifications"), jelena, null)
                .andExpect(jsonPath("$.items[0].type").value("NEW_ANNOUNCEMENT"))
                .andExpect(jsonPath("$.items[0].targetId").value(party))
                .andExpect(jsonPath("$.items[0].preview").value(day + " · 20:00–01:00"));
        send(get("/api/notifications"), marko, null)
                .andExpect(jsonPath("$.items[?(@.type == 'NEW_ANNOUNCEMENT')]").isEmpty());

        // at most 2 active per resident
        send(post("/api/announcements"), marko, body("MOVING", today, today, null, null, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_ANNOUNCEMENTS"));

        // validation
        send(post("/api/announcements"), jelena, body("OTHER", today.minusDays(1), today, null, null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("START_IN_PAST"));
        send(post("/api/announcements"), jelena, body("OTHER", today.plusDays(2), today, null, null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("END_BEFORE_START"));
        send(post("/api/announcements"), jelena, body("OTHER", today, today.plusDays(40), null, null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_LONG"));
        send(post("/api/announcements"), jelena, body("OTHER", today, today, "08:00", null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INCOMPLETE_HOURS"));
        send(post("/api/announcements"), jelena, body("LOUD", today, today, null, null, null))
                .andExpect(status().isBadRequest());

        // soonest first
        send(get("/api/announcements"), jelena, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(renovation))
                .andExpect(jsonPath("$[1].id").value(party));
        send(get("/api/announcements?status=PAST"), jelena, null).andExpect(jsonPath("$.length()").value(0));

        // other entrances see nothing
        send(get("/api/announcements"), outsider, null).andExpect(jsonPath("$.length()").value(0));
        send(get("/api/announcements/{id}", renovation), outsider, null).andExpect(status().isNotFound());

        // only the author or an admin edits / deletes
        send(put("/api/announcements/{id}", renovation), jelena,
                body("RENOVATION", today, today.plusDays(1), null, null, null)).andExpect(status().isForbidden());
        send(put("/api/announcements/{id}", renovation), marko,
                body("RENOVATION", today, today.plusDays(6), null, null, "Produženo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(today.plusDays(6).toString()))
                .andExpect(jsonPath("$.fromTime").doesNotExist());
        send(delete("/api/announcements/{id}", party), jelena, null).andExpect(status().isForbidden());
        send(delete("/api/announcements/{id}", party), admin, null).andExpect(status().isNoContent());
        send(delete("/api/announcements/{id}", renovation), marko, null).andExpect(status().isNoContent());
        send(get("/api/announcements"), jelena, null).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Member who, String body) throws Exception {
        req.header("Authorization", who.bearer());
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private static String body(String kind, LocalDate start, LocalDate end, String from, String to, String note) {
        return """
                {"kind": "%s", "startDate": "%s", "endDate": "%s", "fromTime": %s, "toTime": %s, "note": %s}"""
                .formatted(kind, start, end, q(from), q(to), q(note));
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }
}
