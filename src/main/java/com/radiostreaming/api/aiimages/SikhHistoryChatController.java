package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.saas.security.SaasPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/sikh-history")
public class SikhHistoryChatController {

    private final SikhHistoryChatService chatService;
    private final CurrentSaasUser currentSaasUser;

    public SikhHistoryChatController(SikhHistoryChatService chatService, CurrentSaasUser currentSaasUser) {
        this.chatService = chatService;
        this.currentSaasUser = currentSaasUser;
    }

    @PostMapping("/chat")
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
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
        SaasPrincipal principal = currentSaasUser.optional();
        return chatService.chat(apiKey, principal == null ? null : principal.getUser(), message, history);
    }
}
