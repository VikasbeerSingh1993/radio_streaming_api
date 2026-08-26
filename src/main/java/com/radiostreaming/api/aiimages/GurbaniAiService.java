package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.service.CreditMeteringService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paid Gurbani AI Q&A stub. Credits come from the divine_bliss_web plan.
 */
@Service
public class GurbaniAiService {

    private final CreditMeteringService meteringService;

    public GurbaniAiService(CreditMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    public Map<String, Object> ask(String apiKey, SaasUserDocument sessionUser, String question) {
        CreditMeteringService.MeteredCall call = meteringService.authorizeAndPrepare(
                apiKey, sessionUser, CreditMeteringService.OP_GURBANI_AI, 1);

        String q = question == null ? "" : question.trim();
        String answer = q.isBlank()
                ? "Ask a clear question about a shabad, Ang, or Gurbani theme. Pair this with Gurbani Search for the exact line."
                : "Regarding \"" + q + "\": this is a short Gurbani AI demo answer. "
                        + "A fuller model will explain meaning and context. For the exact verse, use Gurbani Search.";

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("questionLength", q.length());
        meta.put("mode", "stub");
        Map<String, Object> meter = meteringService.commit(call, CreditMeteringService.OP_GURBANI_AI, meta);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "stub");
        body.put("demo", true);
        body.put("role", "assistant");
        body.put("message", answer);
        body.put("metering", meter);
        return body;
    }
}
