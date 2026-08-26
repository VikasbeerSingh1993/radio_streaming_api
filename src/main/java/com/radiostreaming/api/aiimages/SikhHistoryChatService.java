package com.radiostreaming.api.aiimages;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sikh History chat (RAG-ready stub). Useful structured answers until a real model is wired.
 */
@Service
public class SikhHistoryChatService {

    public Map<String, Object> chat(String message, List<Map<String, String>> history) {
        String q = message == null ? "" : message.trim();
        String lower = q.toLowerCase(Locale.ROOT);

        Map<String, Object> answer = pickAnswer(q, lower);

        List<Map<String, Object>> citations = new ArrayList<>();
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("title", "Structured knowledge stub");
        c1.put("note", "Replace with vector RAG + curated Sikh history corpus.");
        citations.add(c1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "stub");
        body.put("demo", true);
        body.put("role", "assistant");
        body.put("message", answer.get("text"));
        body.put("topics", answer.get("topics"));
        body.put("citations", citations);
        body.put("model", "sikh-history-stub-v1");
        body.put("hint", "Wire LLM/RAG later; contract is POST /api/v1/ai/sikh-history/chat");
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
                    + "and equality of all people. The Japji Sahib opens the Guru Granth Sahib. "
                    + "(Demo answer — verify with scholarly sources when the live model is connected.)";
        } else if (lower.contains("khalsa") || lower.contains("vaisakhi") || lower.contains("baisakhi")) {
            topics.add("Khalsa");
            topics.add("Vaisakhi 1699");
            text = "On Vaisakhi 1699 at Anandpur Sahib, Guru Gobind Singh Ji initiated the Khalsa, "
                    + "the community of initiated Sikhs committed to justice, courage, and seva. "
                    + "(Structured stub response for UI wiring.)";
        } else if (lower.contains("harmandir") || lower.contains("golden temple") || lower.contains("amritsar")) {
            topics.add("Harmandir Sahib");
            text = "Harmandir Sahib (the Golden Temple) in Amritsar is the spiritual heart of Sikh devotion, "
                    + "open to all. Continuous kirtan and langar embody seva and equality. "
                    + "(Demo knowledge card — RAG will ground answers in curated texts.)";
        } else if (lower.contains("guru granth") || lower.contains("gurbani")) {
            topics.add("Guru Granth Sahib");
            text = "The Guru Granth Sahib is the eternal Guru of the Sikhs — scripture in Gurmukhi including "
                    + "bani of the Gurus and selected bhagats. Use the Gurbani Search service for verse lookup. "
                    + "(Stub chat; pair with /api/v1/gurbani/search for live text.)";
        } else if (q.isBlank()) {
            topics.add("welcome");
            text = "Ask about Sikh history — Gurus, the Khalsa, Harmandir Sahib, or key events. "
                    + "This endpoint returns structured demo answers until a real model/RAG is configured.";
        } else {
            topics.add("general");
            text = "Regarding \"" + q + "\": this is a structured stub from the Sikh History search engine. "
                    + "A future RAG layer will retrieve from curated Sikh history sources and a language model. "
                    + "For scripture text, try Gurbani Search; for live kirtan, open Listen Live Radio.";
        }
        out.put("text", text);
        out.put("topics", topics);
        return out;
    }
}
