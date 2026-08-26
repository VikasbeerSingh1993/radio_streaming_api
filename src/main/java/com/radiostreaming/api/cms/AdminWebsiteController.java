package com.radiostreaming.api.cms;

import com.radiostreaming.api.service.CurrentAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/website")
public class AdminWebsiteController {

    private final SiteCmsService cmsService;
    private final CurrentAdmin currentAdmin;

    public AdminWebsiteController(SiteCmsService cmsService, CurrentAdmin currentAdmin) {
        this.cmsService = cmsService;
        this.currentAdmin = currentAdmin;
    }

    @GetMapping("/content")
    public Map<String, Object> content() {
        requireSuperAdmin();
        return cmsService.publicBundle();
    }

    @PutMapping("/settings")
    public Map<String, String> saveSettings(@RequestBody Map<String, String> body) {
        requireSuperAdmin();
        return cmsService.saveSettings(body);
    }

    @PutMapping("/pages/{pageKey}")
    public Map<String, Object> savePage(@PathVariable String pageKey, @RequestBody Map<String, Object> body) {
        requireSuperAdmin();
        return cmsService.savePage(pageKey, body);
    }

    @GetMapping("/media")
    public List<Map<String, Object>> media() {
        requireSuperAdmin();
        return cmsService.listMedia(false);
    }

    @PutMapping("/media")
    public Map<String, Object> saveMedia(@RequestBody Map<String, Object> body) {
        requireSuperAdmin();
        return cmsService.saveMedia(body);
    }

    private void requireSuperAdmin() {
        if (!currentAdmin.require().isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a super admin can manage website content");
        }
    }
}
