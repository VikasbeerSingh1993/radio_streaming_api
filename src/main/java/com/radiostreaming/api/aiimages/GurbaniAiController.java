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

    /**
     * Metered voice / live-kirtan search against MySQL {@code bani_search}.
     * Body: {@code transcript} or {@code query}, optional {@code mode}, {@code source}.
     */
    @PostMapping("/voice-search")
    public Map<String, Object> voiceSearch(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        return runSearch(apiKey, body);
    }

    /** Legacy alias — same as voice-search (not a Q&amp;A chatbot). */
    @PostMapping("/ask")
    public Map<String, Object> ask(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        return runSearch(apiKey, body);
    }

    private Map<String, Object> runSearch(String apiKey, Map<String, Object> body) {
        String query = "";
        String mode = "word";
        String source = "all";
        if (body != null) {
            if (body.get("transcript") != null) {
                query = String.valueOf(body.get("transcript"));
            } else if (body.get("query") != null) {
                query = String.valueOf(body.get("query"));
            } else if (body.get("question") != null) {
                query = String.valueOf(body.get("question"));
            } else if (body.get("message") != null) {
                query = String.valueOf(body.get("message"));
            }
            if (body.get("mode") != null) {
                mode = String.valueOf(body.get("mode"));
            }
            if (body.get("source") != null) {
                source = String.valueOf(body.get("source"));
            }
        }
        if ("null".equalsIgnoreCase(query)) {
            query = "";
        }
        SaasPrincipal principal = currentSaasUser.optional();
        return gurbaniAiService.voiceSearch(
                apiKey,
                principal == null ? null : principal.getUser(),
                query,
                mode,
                source);
    }
}
