package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.EventSubmitRequest;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.EventSubmissionDocument;
import com.radiostreaming.api.repository.EventSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class EventSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(EventSubmissionService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration SUBMISSION_TTL = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_SENDS = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final EventSubmissionRepository submissionRepository;
    private final AdminCatalogService adminCatalogService;
    private final PasswordEncoder passwordEncoder;
    private final MailDeliveryService mailDeliveryService;
    private final SecureRandom random = new SecureRandom();

    public EventSubmissionService(
            EventSubmissionRepository submissionRepository,
            AdminCatalogService adminCatalogService,
            PasswordEncoder passwordEncoder,
            MailDeliveryService mailDeliveryService) {
        this.submissionRepository = submissionRepository;
        this.adminCatalogService = adminCatalogService;
        this.passwordEncoder = passwordEncoder;
        this.mailDeliveryService = mailDeliveryService;
    }

    public Map<String, Object> start(EventSubmitRequest request) {
        EventSubmissionDocument submission = new EventSubmissionDocument();
        submission.setTitle(request.getTitle().trim());
        submission.setDate(request.getDate());
        submission.setEndDate(request.getEndDate() != null ? request.getEndDate() : request.getDate());
        submission.setCity(request.getCity().trim());
        submission.setCountry(trimToEmpty(request.getCountry()));
        submission.setCountryCode(trimToEmpty(request.getCountryCode()));
        submission.setState(trimToEmpty(request.getState()));
        submission.setDescription(trimToEmpty(request.getDescription()));
        submission.setAddress(trimToEmpty(request.getAddress()));
        submission.setLatitude(request.getLatitude());
        submission.setLongitude(request.getLongitude());
        submission.setOrganizedBy(trimToEmpty(request.getOrganizedBy()));
        submission.setSubmitterUsername(request.getSubmitterUsername().trim());
        String name = trimToEmpty(request.getSubmitterName());
        submission.setSubmitterName(name.isBlank() ? submission.getSubmitterUsername() : name);
        submission.setSubmitterEmail(request.getSubmitterEmail().trim().toLowerCase(Locale.ROOT));
        submission.setSubmitterPhone(trimToEmpty(request.getSubmitterPhone()));
        submission.setExpiresAt(Instant.now().plus(SUBMISSION_TTL));
        submission = submissionRepository.save(submission);
        sendNewOtp(submission, false);
        return challengeBody(submission, "We sent a verification code to your email.");
    }

    public Map<String, Object> resend(String submissionId) {
        EventSubmissionDocument submission = requireOpen(submissionId);
        sendNewOtp(submission, true);
        return challengeBody(submission, "A new verification code was sent to your email.");
    }

    public EventDocument verify(String submissionId, String otp) {
        EventSubmissionDocument submission = requireOpen(submissionId);
        if (submission.getVerifyAttempts() >= MAX_VERIFY_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect codes. Start the submission again.");
        }
        if (submission.getOtpExpiresAt() == null || Instant.now().isAfter(submission.getOtpExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This code has expired. Request a new one.");
        }
        boolean matches = otp != null && passwordEncoder.matches(otp.trim(), submission.getOtpHash());
        if (!matches) {
            submission.setVerifyAttempts(submission.getVerifyAttempts() + 1);
            submissionRepository.save(submission);
            int left = MAX_VERIFY_ATTEMPTS - submission.getVerifyAttempts();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    left <= 0
                            ? "Too many incorrect codes. Start the submission again."
                            : "Incorrect code. " + left + " attempt" + (left == 1 ? "" : "s") + " left.");
        }
        EventSubmitRequest verified = toRequest(submission);
        EventDocument saved = adminCatalogService.submitVerifiedEvent(verified);
        submission.setConsumed(true);
        submissionRepository.deleteById(submission.getId());
        return saved;
    }

    private void sendNewOtp(EventSubmissionDocument submission, boolean resend) {
        if (resend && submission.getLastSentAt() != null
                && Instant.now().isBefore(submission.getLastSentAt().plus(RESEND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait a minute before requesting another code.");
        }
        if (submission.getSendCount() >= MAX_SENDS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Maximum codes sent for this submission. Start again.");
        }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        submission.setOtpHash(passwordEncoder.encode(otp));
        submission.setOtpExpiresAt(Instant.now().plus(OTP_TTL));
        submission.setLastSentAt(Instant.now());
        submission.setSendCount(submission.getSendCount() + 1);
        submission.setVerifyAttempts(0);
        submissionRepository.save(submission);
        deliverOtp(submission.getSubmitterEmail(), submission.getSubmitterUsername(), otp);
    }

    private void deliverOtp(String email, String username, String otp) {
        mailDeliveryService.sendPlainText(
                email,
                "Your event verification code",
                "Hello " + username + ",\n\n"
                        + "Your Radio Streaming verification code is " + otp + ".\n"
                        + "It expires in 10 minutes. If you did not submit an event, you can ignore this email.\n");
    }

    private EventSubmissionDocument requireOpen(String submissionId) {
        EventSubmissionDocument submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This submission was not found or has expired. Start again."));
        if (submission.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This submission was already verified.");
        }
        if (submission.getExpiresAt() != null && Instant.now().isAfter(submission.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This submission has expired. Start again.");
        }
        return submission;
    }

    private Map<String, Object> challengeBody(EventSubmissionDocument submission, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("submissionId", submission.getId());
        body.put("email", maskEmail(submission.getSubmitterEmail()));
        body.put("otpExpiresInSeconds", OTP_TTL.toSeconds());
        body.put("message", message);
        return body;
    }

    private static EventSubmitRequest toRequest(EventSubmissionDocument submission) {
        EventSubmitRequest request = new EventSubmitRequest();
        request.setTitle(submission.getTitle());
        request.setDate(submission.getDate());
        request.setEndDate(submission.getEndDate());
        request.setCity(submission.getCity());
        request.setCountry(submission.getCountry());
        request.setCountryCode(submission.getCountryCode());
        request.setState(submission.getState());
        request.setDescription(submission.getDescription());
        request.setAddress(submission.getAddress());
        request.setLatitude(submission.getLatitude());
        request.setLongitude(submission.getLongitude());
        request.setOrganizedBy(submission.getOrganizedBy());
        request.setSubmitterUsername(submission.getSubmitterUsername());
        request.setSubmitterName(submission.getSubmitterName());
        request.setSubmitterEmail(submission.getSubmitterEmail());
        request.setSubmitterPhone(submission.getSubmitterPhone());
        return request;
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
