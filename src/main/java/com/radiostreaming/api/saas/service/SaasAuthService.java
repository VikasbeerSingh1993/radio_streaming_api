package com.radiostreaming.api.saas.service;

import com.radiostreaming.api.saas.dto.SaasAccountSettingsRequest;
import com.radiostreaming.api.saas.dto.SaasLoginRequest;
import com.radiostreaming.api.saas.dto.SaasRegisterRequest;
import com.radiostreaming.api.saas.dto.SaasVerifyRegisterRequest;
import com.radiostreaming.api.saas.model.SaasPendingRegistrationDocument;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasPendingRegistrationRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.security.JwtService;
import com.radiostreaming.api.service.MailDeliveryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SaasAuthService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int MAX_SENDS = 5;

    private final SaasUserRepository userRepository;
    private final SaasPendingRegistrationRepository pendingRepository;
    private final SaasUsageEventRepository usageEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailDeliveryService mailDeliveryService;
    private final CreditMeteringService creditMeteringService;
    private final SecureRandom random = new SecureRandom();

    public SaasAuthService(
            SaasUserRepository userRepository,
            SaasPendingRegistrationRepository pendingRepository,
            SaasUsageEventRepository usageEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MailDeliveryService mailDeliveryService,
            CreditMeteringService creditMeteringService) {
        this.userRepository = userRepository;
        this.pendingRepository = pendingRepository;
        this.usageEventRepository = usageEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailDeliveryService = mailDeliveryService;
        this.creditMeteringService = creditMeteringService;
    }

    /** Step 1: collect details, email a one-time code. Account is created only after verify. */
    public Map<String, Object> beginRegistration(SaasRegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String first = blankToNull(request.getFirstName());
        String last = blankToNull(request.getLastName());
        if (first == null || last == null) {
            String display = blankToNull(request.getDisplayName());
            if (display == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name and last name are required");
            }
            String[] parts = display.trim().split("\\s+", 2);
            first = parts[0];
            last = parts.length > 1 ? parts[1] : parts[0];
        }

        Instant now = Instant.now();
        SaasPendingRegistrationDocument pending = pendingRepository.findByEmailIgnoreCase(email)
                .orElseGet(SaasPendingRegistrationDocument::new);
        if (pending.getSendCount() >= MAX_SENDS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification emails. Try again later.");
        }
        if (pending.getId() == null) {
            pending.setCreatedAt(now);
        }
        pending.setEmail(email);
        pending.setFirstName(first.trim());
        pending.setLastName(last.trim());
        pending.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        String otp = String.format("%06d", random.nextInt(1_000_000));
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setOtpExpiresAt(now.plus(OTP_TTL));
        pending.setSendCount(pending.getSendCount() + 1);
        pending.setVerifyAttempts(0);
        pending.setUpdatedAt(now);

        deliverOtp(email, first, otp);
        pendingRepository.save(pending);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "otp_sent");
        body.put("email", email);
        body.put("message", "Check your email for a verification code. Enter it to finish signing up.");
        body.put("otpExpiresInSeconds", OTP_TTL.toSeconds());
        return body;
    }

    public Map<String, Object> verifyRegistration(SaasVerifyRegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        SaasPendingRegistrationDocument pending = pendingRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No pending signup for this email. Start again."));
        if (pending.getOtpExpiresAt() != null && Instant.now().isAfter(pending.getOtpExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code expired. Request a new one.");
        }
        if (pending.getVerifyAttempts() >= 8) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Start again.");
        }
        pending.setVerifyAttempts(pending.getVerifyAttempts() + 1);
        pendingRepository.save(pending);
        if (request.getOtp() == null || !passwordEncoder.matches(request.getOtp().trim(), pending.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect verification code");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            pendingRepository.delete(pending);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Instant now = Instant.now();
        SaasUserDocument user = new SaasUserDocument();
        user.setEmail(email);
        user.setDisplayName((pending.getFirstName() + " " + pending.getLastName()).trim());
        user.setPasswordHash(pending.getPasswordHash());
        user.setRole("USER");
        user.setEnabled(true);
        user.setCreditsRemaining(0);
        user.setCreditsUsed(0);
        user.setCreditsPending(0);
        user.setPlanId(null);
        user.setPlanName(null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        SaasUserDocument saved = userRepository.save(user);
        pendingRepository.delete(pending);
        return authResponse(saved);
    }

    public Map<String, Object> resendRegistrationOtp(String emailRaw) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        SaasPendingRegistrationDocument pending = pendingRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No pending signup for this email"));
        if (pending.getSendCount() >= MAX_SENDS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many emails sent");
        }
        Instant now = Instant.now();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setOtpExpiresAt(now.plus(OTP_TTL));
        pending.setSendCount(pending.getSendCount() + 1);
        pending.setVerifyAttempts(0);
        pending.setUpdatedAt(now);

        deliverOtp(email, pending.getFirstName(), otp);
        pendingRepository.save(pending);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "otp_sent");
        body.put("email", email);
        body.put("message", "Check your email — a new verification code was sent.");
        return body;
    }

    /** Legacy one-shot register kept for older clients — prefer OTP flow. */
    public Map<String, Object> register(SaasRegisterRequest request) {
        return beginRegistration(request);
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
        body.putAll(creditMeteringService.dailyUsageSnapshot(user));
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

    private void deliverOtp(String email, String firstName, String otp) {
        mailDeliveryService.sendPlainText(
                email,
                "Your Divine Bliss verification code",
                "Hello " + firstName + ",\n\n"
                        + "Your Divine Bliss signup code is " + otp + ".\n"
                        + "It expires in 10 minutes.\n\n"
                        + "If you did not create an account, you can ignore this email.\n");
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
        return profile;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
