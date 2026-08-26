package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.service.CreditMeteringService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sikh History chat (RAG-ready stub). Credits are charged from the signed-in plan / API key.
 */
@Service
public class SikhHistoryChatService {

    private final CreditMeteringService meteringService;

    public SikhHistoryChatService(CreditMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    public Map<String, Object> chat(
            String apiKey,
            SaasUserDocument sessionUser,
            String message,
            List<Map<String, String>> history) {
        CreditMeteringService.MeteredCall call = meteringService.authorizeAndPrepare(
                apiKey, sessionUser, CreditMeteringService.OP_SIKH_HISTORY, 1);

        String q = message == null ? "" : message.trim();
        String lower = q.toLowerCase(Locale.ROOT);
        Map<String, Object> answer = pickAnswer(q, lower);

        List<Map<String, Object>> citations = new ArrayList<>();
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("title", "Structured knowledge stub");
        c1.put("note", "Replace with vector RAG + curated Sikh history corpus.");
        citations.add(c1);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("questionLength", q.length());
        meta.put("mode", "stub");
        Map<String, Object> meter = meteringService.commit(call, CreditMeteringService.OP_SIKH_HISTORY, meta);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "stub");
        body.put("demo", true);
        body.put("role", "assistant");
        body.put("message", answer.get("text"));
        body.put("topics", answer.get("topics"));
        body.put("citations", citations);
        body.put("model", "sikh-history-stub-v1");
        body.put("metering", meter);
        if (history != null) {
            body.put("historyTurns", history.size());
        }
        return body;
    }

    private static Map<String, Object> pickAnswer(String q, String lower) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> topics = new ArrayList<>();
        String text;

        if (lower.contains("guru nanak") || lower.contains("nanak")) {
            topics.add("Guru Nanak Dev Ji");
            text = "Guru Nanak Dev Ji (1469–1539) founded Sikhi, teaching Ik Onkar — one Creator — "
                    + "and equality of all people. The Japji Sahib opens the Guru Granth Sahib.";
        } else if (lower.contains("khalsa") || lower.contains("vaisakhi") || lower.contains("baisakhi")) {
            topics.add("Khalsa");
            topics.add("Vaisakhi 1699");
            text = "On Vaisakhi 1699 at Anandpur Sahib, Guru Gobind Singh Ji initiated the Khalsa, "
                    + "the community of initiated Sikhs committed to justice, courage, and seva.";
        } else if (lower.contains("harmandir") || lower.contains("golden temple") || lower.contains("amritsar")) {
            topics.add("Harmandir Sahib");
            text = "Harmandir Sahib (the Golden Temple) in Amritsar is the spiritual heart of Sikh devotion, "
                    + "open to all. Continuous kirtan and langar embody seva and equality.";
        } else if (lower.contains("guru granth") || lower.contains("gurbani")) {
            topics.add("Guru Granth Sahib");
            text = "The Guru Granth Sahib is the eternal Guru of the Sikhs — scripture in Gurmukhi including "
                    + "bani of the Gurus and selected bhagats. Use Gurbani Search for verse lookup.";
        } else if (q.isBlank()) {
            topics.add("welcome");
            text = "Ask about Sikh history — Gurus, the Khalsa, Harmandir Sahib, or key events.";
        } else {
            topics.add("general");
            text = "Regarding \"" + q + "\": this is a short demo answer from Sikh History AI. "
                    + "A fuller knowledge base will be connected later. For scripture, try Gurbani Search.";
        }
        out.put("text", text);
        out.put("topics", topics);
        return out;
    }
}
