package com.radiostreaming.api.aiimages;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/sikh-history")
public class SikhHistoryChatController {

    private final SikhHistoryChatService chatService;

    public SikhHistoryChatController(SikhHistoryChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(@RequestBody(required = false) Map<String, Object> body) {
        String message = "";
        List<Map<String, String>> history = null;
        if (body != null) {
            if (body.get("message") != null) {
                message = String.valueOf(body.get("message"));
            } else if (body.get("query") != null) {
                message = String.valueOf(body.get("query"));
            }
            Object hist = body.get("history");
            if (hist instanceof List<?> list) {
                history = (List<Map<String, String>>) list;
            }
        }
        return chatService.chat(message, history);
    }
}
