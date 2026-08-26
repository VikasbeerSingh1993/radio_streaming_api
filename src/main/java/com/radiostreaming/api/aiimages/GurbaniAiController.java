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
@RequestMapping("/api/v1/ai/gurbani")
public class GurbaniAiController {

    private final GurbaniAiService gurbaniAiService;
    private final CurrentSaasUser currentSaasUser;

    public GurbaniAiController(GurbaniAiService gurbaniAiService, CurrentSaasUser currentSaasUser) {
        this.gurbaniAiService = gurbaniAiService;
        this.currentSaasUser = currentSaasUser;
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        String question = "";
        if (body != null) {
            if (body.get("question") != null) {
                question = String.valueOf(body.get("question"));
            } else if (body.get("message") != null) {
                question = String.valueOf(body.get("message"));
            }
        }
        SaasPrincipal principal = currentSaasUser.optional();
        return gurbaniAiService.ask(apiKey, principal == null ? null : principal.getUser(), question);
    }
}
