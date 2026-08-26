package com.radiostreaming.api.saas.service;

import com.radiostreaming.api.saas.dto.SaasAccountSettingsRequest;
import com.radiostreaming.api.saas.dto.SaasLoginRequest;
import com.radiostreaming.api.saas.dto.SaasRegisterRequest;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SaasAuthService {

    private final SaasUserRepository userRepository;
    private final SaasUsageEventRepository usageEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SaasAuthService(
            SaasUserRepository userRepository,
            SaasUsageEventRepository usageEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.usageEventRepository = usageEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(SaasRegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        Instant now = Instant.now();
        SaasUserDocument user = new SaasUserDocument();
        user.setEmail(email);
        user.setDisplayName(request.getDisplayName() != null && !request.getDisplayName().isBlank()
                ? request.getDisplayName().trim()
                : email.split("@")[0]);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setEnabled(true);
        user.setCreditsRemaining(50);
        user.setCreditsUsed(0);
        user.setCreditsPending(0);
        user.setPlanName("Free starter");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        SaasUserDocument saved = userRepository.save(user);
        return authResponse(saved);
    }

    public Map<String, Object> login(SaasLoginRequest request) {
        SaasUserDocument user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return authResponse(user);
    }

    public Map<String, Object> me(SaasUserDocument user) {
        Map<String, Object> body = profile(user);
        body.put("apiHits", usageEventRepository.countByUserId(user.getId()));
        return body;
    }

    public SaasUserDocument updateSettings(String userId, SaasAccountSettingsRequest request) {
        SaasUserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (request.getAllowOcrOverage() != null) {
            user.setAllowOcrOverage(request.getAllowOcrOverage());
        }
        if (request.getAllowAiImageOverage() != null) {
            user.setAllowAiImageOverage(request.getAllowAiImageOverage());
        }
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    public SaasUserDocument requireById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public SaasUserDocument findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElse(null);
    }

    private Map<String, Object> authResponse(SaasUserDocument user) {
        String token = jwtService.createToken(user.getEmail(), "SAAS_" + user.getRole());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("tokenType", "Bearer");
        body.put("expiresInMs", jwtService.getExpirationMs());
        body.put("user", profile(user));
        return body;
    }

    public static Map<String, Object> profile(SaasUserDocument user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("displayName", user.getDisplayName());
        profile.put("role", user.getRole());
        profile.put("planId", user.getPlanId());
        profile.put("planName", user.getPlanName());
        profile.put("creditsRemaining", user.getCreditsRemaining());
        profile.put("creditsUsed", user.getCreditsUsed());
        profile.put("creditsPending", user.getCreditsPending());
        profile.put("allowOcrOverage", user.isAllowOcrOverage());
        profile.put("allowAiImageOverage", user.isAllowAiImageOverage());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }
}
