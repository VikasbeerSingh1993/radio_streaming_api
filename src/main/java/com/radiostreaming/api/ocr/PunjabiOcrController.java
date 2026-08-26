package com.radiostreaming.api.ocr;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas/ocr")
public class PunjabiOcrController {

    private final PunjabiOcrService ocrService;

    public PunjabiOcrController(PunjabiOcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping(value = "/punjabi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> punjabiMultipart(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ocrService.recognize(apiKey, file, null);
    }

    @PostMapping(value = "/punjabi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> punjabiJson(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        String image = body == null || body.get("imageBase64") == null ? null : String.valueOf(body.get("imageBase64"));
        return ocrService.recognize(apiKey, null, image);
    }
}
