package com.radiostreaming.api.ocr;

import com.radiostreaming.api.saas.service.CreditMeteringService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Punjabi OCR SaaS. Demo/stub until a real OCR provider key is stored in app_credentials.
 * Credit metering is real.
 */
@Service
public class PunjabiOcrService {

    private final CreditMeteringService meteringService;

    public PunjabiOcrService(CreditMeteringService meteringService) {
        this.meteringService = meteringService;
    }

    public Map<String, Object> recognize(String apiKey, MultipartFile file, String imageBase64) {
        CreditMeteringService.MeteredCall call =
                meteringService.authorizeAndPrepare(apiKey, CreditMeteringService.OP_OCR, 1);

        String filename = file != null && !file.isEmpty() ? file.getOriginalFilename() : "inline.png";
        long bytes = 0;
        if (file != null && !file.isEmpty()) {
            bytes = file.getSize();
        } else if (imageBase64 != null && !imageBase64.isBlank()) {
            String payload = imageBase64.contains(",") ? imageBase64.substring(imageBase64.indexOf(',') + 1) : imageBase64;
            bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8)).length;
        }

        String stubText = "ਇਹ ਇੱਕ ਡੈਮੋ ਪੰਜਾਬੀ OCR ਨਤੀਜਾ ਹੈ।\n"
                + "Demo Punjabi OCR — connect a real engine via app_credentials to replace this stub.\n"
                + "Source: " + (filename != null ? filename : "upload");

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", filename);
        meta.put("bytes", bytes);
        meta.put("mode", "stub");

        Map<String, Object> meter = meteringService.commit(call, CreditMeteringService.OP_OCR, meta);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "stub");
        body.put("demo", true);
        body.put("message", "OCR engine not configured. Returning demo Gurmukhi text; credits were charged.");
        body.put("language", "pa");
        body.put("script", "Gurmukhi");
        body.put("text", stubText);
        body.put("confidence", 0.51);
        body.put("filename", filename);
        body.put("bytes", bytes);
        body.put("metering", meter);
        return body;
    }
}
