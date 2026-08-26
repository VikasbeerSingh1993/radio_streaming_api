package com.radiostreaming.api.cms;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site")
public class PublicSiteController {

    private final SiteCmsService cmsService;

    public PublicSiteController(SiteCmsService cmsService) {
        this.cmsService = cmsService;
    }

    @GetMapping("/content")
    public Map<String, Object> content() {
        return cmsService.publicBundle();
    }
}
