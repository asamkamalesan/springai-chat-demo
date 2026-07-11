package com.sam.springai_chat_demo.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import com.sam.springai_chat_demo.controller.ChatController;

/**
 * Verifies {@link AuthTokenFilter} behaviour when {@code app.auth.token} is configured
 * (to {@code test-secret} via src/test/resources/application.yml). The ChatClient is a
 * deep-stub mock, so no real Anthropic call is made when a request passes the filter.
 */
@WebMvcTest(ChatController.class)
class AuthTokenFilterTest {

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class Mocks {
        @Bean
        ChatClient chatClient() {
            ChatClient client = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
            when(client.prompt().user(anyString()).call().content()).thenReturn("mocked reply");
            return client;
        }
    }

    @Test
    void validToken_passesThroughToController() throws Exception {
        mockMvc.perform(get("/chat").param("message", "hi")
                        .header("Authorization", "Bearer test-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string("mocked reply"));
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/chat").param("message", "hi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(containsString("Authorization header")));
    }

    @Test
    void malformedHeader_withoutBearerPrefix_returns401() throws Exception {
        mockMvc.perform(get("/chat").param("message", "hi")
                        .header("Authorization", "test-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("Authorization header")));
    }

    @Test
    void wrongToken_returns401() throws Exception {
        mockMvc.perform(get("/chat").param("message", "hi")
                        .header("Authorization", "Bearer not-the-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid auth token."));
    }

    @Test
    void streamEndpoint_isAlsoGuarded() throws Exception {
        mockMvc.perform(get("/chat/stream").param("message", "hi"))
                .andExpect(status().isUnauthorized());
    }
}
