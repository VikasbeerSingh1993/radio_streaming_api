package com.radiostreaming.api.aiimages;

import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.service.CreditMeteringService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sikh-related AI image generation. Stub until provider key is in app_credentials.
 * Credits are charged per image. Website uses JWT; API keys are for external apps.
 */
@Service
public class AiImageService {

    private final CreditMeteringService meteringService;

    public AiImageService(CreditMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    public Map<String, Object> generate(String apiKey, SaasUserDocument sessionUser, String prompt, int count) {
        int n = Math.min(8, Math.max(1, count));
        String safePrompt = prompt == null || prompt.isBlank()
                ? "Golden Harmandir Sahib at dusk, respectful Sikh spiritual art"
                : prompt.trim();

        CreditMeteringService.MeteredCall call =
                meteringService.authorizeAndPrepare(apiKey, sessionUser, CreditMeteringService.OP_AI_IMAGE, n);

        List<Map<String, Object>> images = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> img = new LinkedHashMap<>();
            String id = UUID.randomUUID().toString();
            img.put("id", id);
            img.put("prompt", safePrompt);
            img.put("status", "stub");
            // Deterministic placeholder SVG data URL (no external model)
            String svg = stubSvg(safePrompt, i + 1);
            img.put("url", "data:image/svg+xml;utf8," + encodeSvg(svg));
            img.put("width", 768);
            img.put("height", 768);
            images.add(img);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("prompt", safePrompt);
        meta.put("count", n);
        meta.put("mode", "stub");

        Map<String, Object> meter = meteringService.commit(call, CreditMeteringService.OP_AI_IMAGE, meta);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "stub");
        body.put("demo", true);
        body.put("message", "Image model not configured. Returning placeholder artwork; credits were charged per image.");
        body.put("prompt", safePrompt);
        body.put("images", images);
        body.put("metering", meter);
        return body;
    }

    private static String stubSvg(String prompt, int index) {
        String shortPrompt = prompt.length() > 48 ? prompt.substring(0, 45) + "…" : prompt;
        return """
                <svg xmlns='http://www.w3.org/2000/svg' width='768' height='768' viewBox='0 0 768 768'>
                  <defs>
                    <linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
                      <stop offset='0%%' stop-color='#3B2F63'/>
                      <stop offset='55%%' stop-color='#5C3D6E'/>
                      <stop offset='100%%' stop-color='#E88F2A'/>
                    </linearGradient>
                  </defs>
                  <rect width='768' height='768' fill='url(#g)'/>
                  <circle cx='384' cy='300' r='90' fill='#FBBF24' fill-opacity='0.85'/>
                  <text x='384' y='460' text-anchor='middle' fill='#FFF8F0' font-family='Georgia,serif' font-size='28'>
                    Divine Bliss · stub #%d
                  </text>
                  <text x='384' y='510' text-anchor='middle' fill='#F5E6D3' font-family='Georgia,serif' font-size='18'>
                    %s
                  </text>
                  <text x='384' y='700' text-anchor='middle' fill='#F5E6D3' font-family='sans-serif' font-size='14'>
                    Demo placeholder — wire provider via app_credentials
                  </text>
                </svg>
                """.formatted(index, escapeXml(shortPrompt));
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String encodeSvg(String svg) {
        return java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
