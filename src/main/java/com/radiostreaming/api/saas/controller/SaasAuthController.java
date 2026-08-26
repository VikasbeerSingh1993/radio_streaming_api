package com.radiostreaming.api.saas.controller;

import com.radiostreaming.api.saas.dto.SaasAccountSettingsRequest;
import com.radiostreaming.api.saas.dto.SaasLoginRequest;
import com.radiostreaming.api.saas.dto.SaasRegisterRequest;
import com.radiostreaming.api.saas.dto.SaasVerifyRegisterRequest;
import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.saas.service.SaasAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas/auth")
public class SaasAuthController {

    private final SaasAuthService authService;
    private final CurrentSaasUser currentSaasUser;

    public SaasAuthController(SaasAuthService authService, CurrentSaasUser currentSaasUser) {
        this.authService = authService;
        this.currentSaasUser = currentSaasUser;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody SaasRegisterRequest request) {
        return authService.beginRegistration(request);
    }

    @PostMapping("/register/verify")
    public Map<String, Object> verify(@Valid @RequestBody SaasVerifyRegisterRequest request) {
        return authService.verifyRegistration(request);
    }

    @PostMapping("/register/resend")
    public Map<String, Object> resend(@RequestBody Map<String, String> body) {
        return authService.resendRegistrationOtp(body == null ? null : body.get("email"));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody SaasLoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.me(currentSaasUser.require().getUser());
    }

    @PutMapping("/settings")
    public Map<String, Object> settings(@RequestBody SaasAccountSettingsRequest request) {
        return SaasAuthService.profile(authService.updateSettings(currentSaasUser.require().getUserId(), request));
    }
}
