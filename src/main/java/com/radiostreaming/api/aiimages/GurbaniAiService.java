package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.dto.GurbaniSearchHit;
import com.radiostreaming.api.dto.GurbaniSearchPage;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.service.CreditMeteringService;
import com.radiostreaming.api.service.GurbaniSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paid Gurbani AI Search: voice/live transcript → MySQL {@code bani_search}.
 * Credits come from the divine_bliss_web plan (not Q&amp;A chat).
 */
@Service
public class GurbaniAiService {

    private final CreditMeteringService meteringService;
    private final GurbaniSearchService gurbaniSearchService;

    public GurbaniAiService(CreditMeteringService meteringService, GurbaniSearchService gurbaniSearchService) {
        this.meteringService = meteringService;
        this.gurbaniSearchService = gurbaniSearchService;
    }

    public Map<String, Object> voiceSearch(
            String apiKey,
            SaasUserDocument sessionUser,
            String query,
            String mode,
            String source) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Speak or paste Gurbani text to search. This tool searches the Gurbani database — it is not a Q&A chat.");
        }

        CreditMeteringService.MeteredCall call = meteringService.authorizeAndPrepare(
                apiKey, sessionUser, CreditMeteringService.OP_GURBANI_AI, 1);

        String searchMode = (mode == null || mode.isBlank()) ? "word" : mode.trim();
        String searchSource = (source == null || source.isBlank()) ? "all" : source.trim();
        GurbaniSearchPage page = gurbaniSearchService.search(q, searchMode, searchSource, 0, 20);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("queryLength", q.length());
        meta.put("mode", "voice_search");
        meta.put("searchMode", searchMode);
        meta.put("source", searchSource);
        meta.put("hitCount", page.getItems() == null ? 0 : page.getItems().size());
        Map<String, Object> meter = meteringService.commit(call, CreditMeteringService.OP_GURBANI_AI, meta);

        List<Map<String, Object>> items = new ArrayList<>();
        if (page.getItems() != null) {
            for (GurbaniSearchHit hit : page.getItems()) {
                items.add(toItem(hit));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", page.isAvailable() ? "ok" : "unavailable");
        body.put("available", page.isAvailable());
        body.put("query", q);
        body.put("total", page.getTotal());
        body.put("items", items);
        body.put("message", page.getMessage());
        body.put("metering", meter);
        return body;
    }

    /** @deprecated Prefer {@link #voiceSearch}; kept for older clients. */
    public Map<String, Object> ask(String apiKey, SaasUserDocument sessionUser, String question) {
        return voiceSearch(apiKey, sessionUser, question, "word", "all");
    }

    private static Map<String, Object> toItem(GurbaniSearchHit hit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("verseId", hit.getVerseId());
        m.put("shabadId", hit.getShabadId());
        m.put("sourceCode", hit.getSourceCode());
        m.put("ang", hit.getAng() != null ? hit.getAng() : hit.getPageNo());
        m.put("pageNo", hit.getPageNo());
        m.put("lineNo", hit.getLineNo());
        m.put("gurmukhi", hit.getGurmukhi());
        m.put("unicode", hit.getUnicode());
        m.put("transliteration", hit.getTransliteration());
        m.put("translation", hit.getTranslation());
        m.put("translationEnglish", hit.getTranslationEnglish());
        m.put("translationPunjabi", hit.getTranslationPunjabi());
        m.put("translationHindi", hit.getTranslationHindi());
        m.put("writer", hit.getWriter());
        m.put("raag", hit.getRaag());
        return m;
    }
}
