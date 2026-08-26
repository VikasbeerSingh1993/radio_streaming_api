package com.radiostreaming.api.aiimages;

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

    public AiImageController(AiImageService aiImageService) {
        this.aiImageService = aiImageService;
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
        return aiImageService.generate(apiKey, prompt, count);
    }
}
