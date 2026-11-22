package com.entry;

import static com.entry.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.entry.support.ApiTestSupport;
import com.entry.support.ApiTestSupport.Member;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.storage.local-dir=${java.io.tmpdir}/entry-test-uploads")
class FeaturesIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    ApiTestSupport api;
    Member admin;
    Member resident;
    Member outsider;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc);
        admin = api.newAdmin("Ana");
        resident = api.newResident(admin, "Marko");
        outsider = api.newAdmin("Zoran"); // admin of a different entrance
    }

    @Test
    void imagesPostsLikesAndComments() throws Exception {
        // upload an image, then post it
        String imageId = json(mvc.perform(multipart("/api/files").file(png("file")).header("Authorization", resident.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.startsWith("/api/files/")))
                .andReturn(), "$.id");

        MvcResult created = mvc.perform(post("/api/posts").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"New plants in the hallway\", \"imageIds\": [\"" + imageId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andReturn();
        String postId = json(created, "$.id");
        String thumbUrl = json(created, "$.images[0].thumbnailUrl");

        // signed URL works without a token; a tampered one does not
        mvc.perform(get(thumbUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(thumbUrl.replaceAll("sig=[^&]+", "sig=forged"))).andExpect(status().isNotFound());

        // the same image cannot be attached by someone else
        mvc.perform(post("/api/posts").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\": [\"" + imageId + "\"]}"))
                .andExpect(status().isBadRequest());

        // feed, likes, comments
        mvc.perform(get("/api/posts").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].author.firstName").value("Marko"));
        mvc.perform(put("/api/posts/{id}/like", postId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/posts/{id}/like", postId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.likeCount").value(1)); // idempotent
        mvc.perform(post("/api/posts/{id}/comments", postId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Looks great!\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/posts/{id}", postId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mvc.perform(get("/api/posts/{id}/comments", postId).header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$[0].text").value("Looks great!"));

        // other entrances cannot see it; non-authors cannot delete it, admins can
        mvc.perform(get("/api/posts/{id}", postId).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/posts/{id}", postId).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void feedPaginationWithCursor() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/posts").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"post " + i + "\"}"));
        }
        MvcResult page1 = mvc.perform(get("/api/posts?size=2").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].text").value("post 2"))
                .andReturn();
        String cursor = json(page1, "$.nextCursor");
        mvc.perform(get("/api/posts?size=2&cursor=" + cursor).header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].text").value("post 0"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void pollsVotingResultsAndExpiry() throws Exception {
        String pollId = json(mvc.perform(post("/api/polls").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Better lighting in the parking area?\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        mvc.perform(put("/api/polls/{id}/vote", pollId).header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": true}"))
                .andExpect(jsonPath("$.myVote").value(true))
                .andExpect(jsonPath("$.results.yesPercent").value(100));
        mvc.perform(put("/api/polls/{id}/vote", pollId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": false}"))
                .andExpect(jsonPath("$.results.totalVotes").value(2))
                .andExpect(jsonPath("$.results.yesPercent").value(50))
                .andExpect(jsonPath("$.results.noPercent").value(50));
        // change vote
        mvc.perform(put("/api/polls/{id}/vote", pollId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": true}"))
                .andExpect(jsonPath("$.results.yesCount").value(2))
                .andExpect(jsonPath("$.results.totalVotes").value(2));

        // outsiders cannot vote
        mvc.perform(put("/api/polls/{id}/vote", pollId).header("Authorization", outsider.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": true}"))
                .andExpect(status().isNotFound());

        // after expiry: no more voting, listed under EXPIRED
        jdbc.update("update poll set expires_at = now() - interval '1 minute' where id = ?", UUID.fromString(pollId));
        mvc.perform(put("/api/polls/{id}/vote", pollId).header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLL_EXPIRED"));
        mvc.perform(get("/api/polls?status=EXPIRED").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$[0].id").value(pollId))
                .andExpect(jsonPath("$[0].expired").value(true));

        mvc.perform(post("/api/polls").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Past?\", \"expiresAt\": \"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatHistoryAndAvatar() throws Exception {
        mvc.perform(post("/api/chat/messages").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"When is the elevator fixed?\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/chat/messages").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/chat/messages").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[0].text").value("When is the elevator fixed?"))
                .andExpect(jsonPath("$.items[0].sender.firstName").value("Marko"));
        mvc.perform(get("/api/chat/messages").header("Authorization", outsider.bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").file(png("file"))
                        .header("Authorization", resident.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty());
        mvc.perform(get("/api/users/{id}", resident.id()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty());
    }

    @Test
    void notificationsAreCreatedForOtherMembers() throws Exception {
        mvc.perform(post("/api/polls").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"Is the cleaning schedule good enough?\"}"));

        // fan-out is asynchronous; expect MEMBERSHIP_APPROVED (from setUp) + NEW_POLL
        long count = 0;
        for (int i = 0; i < 50 && count < 2; i++) {
            Thread.sleep(100);
            count = Long.parseLong(json(mvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", resident.bearer())).andReturn(), "$.count"));
        }
        assertThat(count).isEqualTo(2);

        mvc.perform(get("/api/notifications").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("NEW_POLL"))
                .andExpect(jsonPath("$.items[0].preview").value("Is the cleaning schedule good enough?"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(post("/api/notifications/read-all").header("Authorization", resident.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.count").value(0));

        mvc.perform(put("/api/notifications/settings").header("Authorization", resident.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pushEnabled\": true, \"newMessages\": false, \"newPolls\": true, \"newPosts\": true}"))
                .andExpect(jsonPath("$.newMessages").value(false));
    }

    private static MockMultipartFile png(String name) throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile(name, "photo.png", "image/png", out.toByteArray());
    }
}
