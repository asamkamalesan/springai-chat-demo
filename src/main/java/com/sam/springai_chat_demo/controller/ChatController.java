package com.sam.springai_chat_demo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam @NonNull String message) {
        // Exceptions propagate to GlobalExceptionHandler.
        return chatClient.prompt().user(message).call().content();
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam @NonNull String message) {
        return chatClient.prompt().user(message).stream().content()
                // Errors surface mid-stream (the 200 response has already begun),
                // so map them to a final text chunk rather than an HTTP status.
                // GlobalExceptionHandler covers the synchronous /chat path.
                .onErrorResume(e -> Flux.just("Error: " + e.getMessage()));
    }

}
