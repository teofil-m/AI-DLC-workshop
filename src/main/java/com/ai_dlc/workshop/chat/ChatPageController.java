package com.ai_dlc.workshop.chat;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the Thymeleaf chat page at GET /chat.
 * The page is permitted for anonymous access in non-prod profiles (see SecurityConfig).
 */
@Controller
@RequestMapping("/chat")
public class ChatPageController {

    @GetMapping
    public String chatPage() {
        return "chat"; // resolves to src/main/resources/templates/chat.html
    }
}
