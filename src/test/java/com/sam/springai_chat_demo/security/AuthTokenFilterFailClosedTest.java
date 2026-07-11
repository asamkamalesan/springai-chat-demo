package com.sam.springai_chat_demo.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sam.springai_chat_demo.controller.ChatController;

/**
 * When {@code app.auth.token} is blank, the filter must fail closed — reject every
 * guarded request with 401 even if a well-formed bearer token is supplied.
 */
@WebMvcTest(ChatController.class)
@TestPropertySource(properties = "app.auth.token=")
class AuthTokenFilterFailClosedTest {

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class Mocks {
        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class, RETURNS_DEEP_STUBS);
        }
    }

    @Test
    void blankExpectedToken_rejectsEvenWellFormedToken() throws Exception {
        mockMvc.perform(get("/chat").param("message", "hi")
                        .header("Authorization", "Bearer anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("not configured")));
    }
}
