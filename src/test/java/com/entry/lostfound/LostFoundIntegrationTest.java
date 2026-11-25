package com.entry.lostfound;

import static com.entry.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.entry.TestcontainersConfiguration;
import com.entry.support.ApiTestSupport;
import com.entry.support.ApiTestSupport.Member;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.storage.local-dir=${java.io.tmpdir}/entry-test-uploads")
class LostFoundIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void neighboursPostRespondAndResolveLostAndFoundItems() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        Member admin = api.newAdmin("Ana");
        Member marko = api.newResident(admin, "Marko");
        Member jelena = api.newResident(admin, "Jelena");
        Member outsider = api.newAdmin("Zoran");

        String photo = json(mvc.perform(multipart("/api/files").file(png())
                .header("Authorization", marko.bearer())).andExpect(status().isCreated()).andReturn(), "$.id");

        String keys = json(send(post("/api/lost-found"), marko,
                body("FOUND", "KEYS", "Ključevi sa crvenim privezkom", "Ispred lifta, 2. sprat", photo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photo.thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$.author.apartment").value("9"))
                .andExpect(jsonPath("$.responders.length()").value(0))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andReturn(), "$.id");
        String cat = json(send(post("/api/lost-found"), jelena, body("LOST", "PET", "Siva mačka", null, null))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        // someone else's photo can't be attached; validation
        send(post("/api/lost-found"), jelena, body("FOUND", "OTHER", "X", null, photo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
        send(post("/api/lost-found"), jelena, body("FOUND", "SOCKS", "X", null, null))
                .andExpect(status().isBadRequest());
        send(post("/api/lost-found"), jelena, body("FOUND", "OTHER", " ", null, null))
                .andExpect(status().isBadRequest());

        // neighbours are notified (asynchronously; Jelena also has MEMBERSHIP_APPROVED)
        awaitUnread(jelena, 2);
        send(get("/api/notifications"), jelena, null)
                .andExpect(jsonPath("$.items[0].type").value("NEW_LOST_FOUND"))
                .andExpect(jsonPath("$.items[0].preview").value("FOUND: Ključevi sa crvenim privezkom"));

        // newest first, filter by kind
        send(get("/api/lost-found"), marko, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(cat));
        send(get("/api/lost-found?kind=FOUND"), marko, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(keys));

        // "it's mine": idempotent, tells the author once, not on your own post
        for (int i = 0; i < 2; i++) {
            send(put("/api/lost-found/{id}/response", keys), jelena, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responders.length()").value(1))
                    .andExpect(jsonPath("$.responders[0].firstName").value("Jelena"));
        }
        send(put("/api/lost-found/{id}/response", keys), marko, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OWN_ITEM"));
        awaitUnread(marko, 3); // MEMBERSHIP_APPROVED + NEW_LOST_FOUND (cat) + LOST_FOUND_RESPONSE
        send(get("/api/notifications"), marko, null)
                .andExpect(jsonPath("$.items[0].type").value("LOST_FOUND_RESPONSE"))
                .andExpect(jsonPath("$.items[0].targetId").value(keys));
        send(delete("/api/lost-found/{id}/response", keys), jelena, null)
                .andExpect(jsonPath("$.responders.length()").value(0));
        send(put("/api/lost-found/{id}/response", keys), jelena, null).andExpect(status().isOk());

        // other entrances see nothing
        send(get("/api/lost-found"), outsider, null).andExpect(jsonPath("$.length()").value(0));
        send(put("/api/lost-found/{id}/response", keys), outsider, null).andExpect(status().isNotFound());

        // only the author or an admin edits / resolves
        send(put("/api/lost-found/{id}", keys), jelena, body("FOUND", "KEYS", "Moje", null, null))
                .andExpect(status().isForbidden());
        send(put("/api/lost-found/{id}/resolved", keys), jelena, null).andExpect(status().isForbidden());
        send(put("/api/lost-found/{id}", keys), marko, body("FOUND", "KEYS", "Ključevi", "Ulaz", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ključevi"))
                .andExpect(jsonPath("$.photo").doesNotExist());
        send(put("/api/lost-found/{id}/resolved", keys), marko, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        send(put("/api/lost-found/{id}/response", keys), admin, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_RESOLVED"));
        send(get("/api/lost-found"), jelena, null).andExpect(jsonPath("$.length()").value(1));
        send(get("/api/lost-found?status=RESOLVED"), jelena, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(keys))
                .andExpect(jsonPath("$[0].responders.length()").value(1));
        send(delete("/api/lost-found/{id}/resolved", keys), admin, null)
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        send(delete("/api/lost-found/{id}", cat), marko, null).andExpect(status().isForbidden());
        send(delete("/api/lost-found/{id}", cat), admin, null).andExpect(status().isNoContent());
        send(delete("/api/lost-found/{id}", keys), marko, null).andExpect(status().isNoContent());
        send(get("/api/lost-found"), jelena, null).andExpect(jsonPath("$.length()").value(0));
    }

    private void awaitUnread(Member who, long expected) throws Exception {
        long count = 0;
        for (int i = 0; i < 50 && count < expected; i++) {
            Thread.sleep(100);
            count = Long.parseLong(json(send(get("/api/notifications/unread-count"), who, null).andReturn(),
                    "$.count"));
        }
        assertThat(count).isEqualTo(expected);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Member who, String body) throws Exception {
        req.header("Authorization", who.bearer());
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private static String body(String kind, String category, String title, String location, String photoId) {
        return """
                {"kind": "%s", "category": "%s", "title": "%s", "description": "Javite se",
                 "location": %s, "photoFileId": %s}"""
                .formatted(kind, category, title, q(location), q(photoId));
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray());
    }
}
