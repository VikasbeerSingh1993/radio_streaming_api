package com.radiostreaming.api.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends transactional email using GMAIL credentials from Mongo {@code app_credentials}.
 */
@Service
public class MailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);

    private final CredentialService credentialService;

    public MailDeliveryService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    public void sendPlainText(String to, String subject, String body) {
        JavaMailSender sender = credentialService.mailSender()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Email delivery is not configured. Add GMAIL host, port, username, and password in admin Settings."));
        String from = credentialService.mailFrom();
        if (from == null || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured. Set a from address in GMAIL credentials.");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(new InternetAddress(from));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("Email sent to {}", maskEmail(to));
        } catch (Exception ex) {
            log.warn("Failed to send email to {}", maskEmail(to), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not send the verification email. Check the address and try again.");
        }
    }

    public boolean isConfigured() {
        return credentialService.mailSender().isPresent()
                && credentialService.mailFrom() != null
                && !credentialService.mailFrom().isBlank();
    }

    private static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
