package com.radiostreaming.api.ocr;

import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.saas.security.SaasPrincipal;
import com.radiostreaming.api.saas.service.CreditMeteringService;
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
    private final CurrentSaasUser currentSaasUser;

    public PunjabiOcrController(PunjabiOcrService ocrService, CurrentSaasUser currentSaasUser) {
        this.ocrService = ocrService;
        this.currentSaasUser = currentSaasUser;
    }

    @PostMapping(value = "/punjabi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> punjabiMultipart(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        SaasPrincipal principal = currentSaasUser.optional();
        return ocrService.recognize(apiKey, principal == null ? null : principal.getUser(), file, null);
    }

    @PostMapping(value = "/punjabi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> punjabiJson(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody(required = false) Map<String, Object> body) {
        String image = body == null || body.get("imageBase64") == null ? null : String.valueOf(body.get("imageBase64"));
        SaasPrincipal principal = currentSaasUser.optional();
        return ocrService.recognize(apiKey, principal == null ? null : principal.getUser(), null, image);
    }
}
