package com.entry.craftsman;

import static com.entry.support.ApiTestSupport.json;
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
class CraftsmenIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void neighboursAddEditAndRecommendCraftsmen() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        Member admin = api.newAdmin("Ana");
        Member marko = api.newResident(admin, "Marko");
        Member jelena = api.newResident(admin, "Jelena");
        Member outsider = api.newAdmin("Zoran");

        String photo = json(mvc.perform(multipart("/api/files").file(png())
                .header("Authorization", marko.bearer())).andExpect(status().isCreated()).andReturn(), "$.id");

        String plumber = json(send(post("/api/craftsmen"), marko, body("Pera Vodoinstalater", "PLUMBER", photo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photo.thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$.addedBy").value("Marko Tester"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andReturn(), "$.id");
        String electrician = json(send(post("/api/craftsmen"), jelena, body("Aca Struja", "ELECTRICIAN", null))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        // someone else's photo can't be attached
        send(post("/api/craftsmen"), jelena, body("X", "OTHER", photo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
        send(post("/api/craftsmen"), jelena, "{\"name\": \"X\", \"trade\": \"OTHER\", \"phone\": \"abc\"}")
                .andExpect(status().isBadRequest());

        // recommending is idempotent and sorts the list
        for (int i = 0; i < 2; i++) {
            send(put("/api/craftsmen/{id}/recommend", electrician), marko, null)
                    .andExpect(jsonPath("$.recommendations").value(1))
                    .andExpect(jsonPath("$.recommendedByMe").value(true));
        }
        send(get("/api/craftsmen"), marko, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(electrician))
                .andExpect(jsonPath("$[0].recommendedByMe").value(true))
                .andExpect(jsonPath("$[0].canEdit").value(false));
        send(get("/api/craftsmen?trade=PLUMBER"), jelena, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recommendedByMe").value(false));
        send(delete("/api/craftsmen/{id}/recommend", electrician), marko, null)
                .andExpect(jsonPath("$.recommendations").value(0));

        // other entrances see nothing and can't touch it
        send(get("/api/craftsmen"), outsider, null).andExpect(jsonPath("$.length()").value(0));
        send(put("/api/craftsmen/{id}/recommend", plumber), outsider, null).andExpect(status().isNotFound());

        // only the author or an admin edits / deletes
        send(put("/api/craftsmen/{id}", plumber), jelena, body("Hacked", "PLUMBER", null))
                .andExpect(status().isForbidden());
        send(put("/api/craftsmen/{id}", plumber), admin, body("Pera V.", "PLUMBER", photo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pera V."))
                .andExpect(jsonPath("$.photo").isNotEmpty());
        send(put("/api/craftsmen/{id}", plumber), marko, body("Pera V.", "PLUMBER", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo").doesNotExist());
        send(delete("/api/craftsmen/{id}", electrician), marko, null).andExpect(status().isForbidden());
        send(delete("/api/craftsmen/{id}", electrician), jelena, null).andExpect(status().isNoContent());
        send(delete("/api/craftsmen/{id}", plumber), admin, null).andExpect(status().isNoContent());
        send(get("/api/craftsmen"), marko, null).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Member who, String body) throws Exception {
        req.header("Authorization", who.bearer());
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private static String body(String name, String trade, String photoId) {
        return """
                {"name": "%s", "trade": "%s", "phone": "+381 64 123 4567", "email": "majstor@example.com",
                 "description": "Brz i pouzdan", "photoFileId": %s}"""
                .formatted(name, trade, photoId == null ? "null" : "\"" + photoId + "\"");
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray());
    }
}
