package com.sam.springai_chat_demo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("""
                You are an expert Splunk analyst and SRE assistant.

                When the user describes a goal or pastes log data:
                - Generate a correct, idiomatic Splunk SPL query for it.
                - Briefly explain what each part of the query does.
                - If sample results are included, analyze them and call out anomalies, trends, and next steps.

                Filter early and avoid needless subsearches. If the request is ambiguous, state your \
                assumptions. Keep answers concise and in plain text (responses are shown as plain text, \
                not rendered markdown).""")
            .build();
    }
}
