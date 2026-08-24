package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.LoginRequest;
import com.radiostreaming.api.dto.LoginResponse;
import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.repository.AdminUserRepository;
import com.radiostreaming.api.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminUserRepository adminUserRepository;
    private final AdminDirectory adminDirectory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final boolean resetPassword;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            AdminDirectory adminDirectory,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.admin.username:admin}") String bootstrapUsername,
            @Value("${app.admin.password:Admin@12345}") String bootstrapPassword,
            @Value("${app.admin.reset-password:false}") boolean resetPassword) {
        this.adminUserRepository = adminUserRepository;
        this.adminDirectory = adminDirectory;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
        this.resetPassword = resetPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void seedAdminUser() {
        adminUserRepository.findByUsername(bootstrapUsername).ifPresentOrElse(existing -> {
            existing.setRole("SUPER_ADMIN");
            existing.setEnabled(true);
            existing.setOwnRecordsOnly(false);
            existing.setPermissions(AdminPermissions.fullAccess());
            if (resetPassword) {
                existing.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                log.info("Reset password for super admin '{}'", bootstrapUsername);
            }
            adminDirectory.put(adminUserRepository.save(existing));
            log.info("Ensured super admin '{}' has full access", bootstrapUsername);
        }, () -> {
            AdminUser admin = new AdminUser();
            admin.setUsername(bootstrapUsername);
            admin.setDisplayName("Super Admin");
            admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            admin.setRole("SUPER_ADMIN");
            admin.setEnabled(true);
            admin.setOwnRecordsOnly(false);
            admin.setPermissions(AdminPermissions.fullAccess());
            admin.setCreatedAt(Instant.now());
            admin.setCreatedBy("system");
            AdminUser saved = adminUserRepository.save(admin);
            adminDirectory.put(saved);
            log.info("Seeded super admin '{}' in collection 'admins'", bootstrapUsername);
        });
    }

    public LoginResponse login(LoginRequest request) {
        AdminUser admin = adminUserRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!admin.isEnabledAccount()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        admin.setLastLoginAt(Instant.now());
        AdminUser saved = adminUserRepository.save(admin);
        adminDirectory.put(saved);
        String tokenRole = saved.isSuperAdmin() ? "SUPER_ADMIN" : "SUB_ADMIN";
        String token = jwtService.createToken(saved.getUsername(), tokenRole);
        return new LoginResponse(token, jwtService.getExpirationMs(), saved);
    }
}
