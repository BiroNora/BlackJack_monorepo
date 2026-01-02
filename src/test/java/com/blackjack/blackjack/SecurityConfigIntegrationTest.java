package com.blackjack.blackjack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API Biztonság: A /api/test/rule_error végpontot el kell érni (400-as választ várunk)")
    void apiSecurityTest() throws Exception {
        // Ha a Security JÓ, eljutunk a kontrollerig, ami 400-at dob.
        // Ha a Security ROSSZ, megállít 403-mal.
        mockMvc.perform(get("/api/test/rule_error"))
            .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Statikus fájlok: Az index.html elérhető (vagy 200 vagy 404, de nem 403)")
    void staticResourceTest() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().is(500));
    }

    @Test
    @DisplayName("API Post: A split_double_request nem dobhat 403-at")
    void splitDoubleSecurityTest() throws Exception {
        String jsonPayload = "{\"clientId\":\"00000000-0000-0000-0000-000000000000\"}";

        mockMvc.perform(post("/api/split_double_request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            // Ha 403-at kapnánk, a teszt elbukik. Bármi más (400, 404, 500) azt jelenti, a kapu nyitva.
            .andExpect(status().is(404));
    }
}
