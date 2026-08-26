package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.saas.security.SaasPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas/ai")
public class AiImageController {

    private final AiImageService aiImageService;
    private final CurrentSaasUser currentSaasUser;

    public AiImageController(AiImageService aiImageService, CurrentSaasUser currentSaasUser) {
        this.aiImageService = aiImageService;
        this.currentSaasUser = currentSaasUser;
    }

    @PostMapping("/images")
    public Map<String, Object> generate(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        String prompt = body == null || body.get("prompt") == null ? "" : String.valueOf(body.get("prompt"));
        int count = 1;
        if (body != null && body.get("count") instanceof Number n) {
            count = n.intValue();
        }
        SaasPrincipal principal = currentSaasUser.optional();
        return aiImageService.generate(apiKey, principal == null ? null : principal.getUser(), prompt, count);
    }
}
