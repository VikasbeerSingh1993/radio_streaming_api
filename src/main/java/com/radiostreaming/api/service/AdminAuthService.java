package com.radiostreaming.api.service;

import com.radiostreaming.api.credentials.CentralCredentialCatalog;
import com.radiostreaming.api.dto.LoginRequest;
import com.radiostreaming.api.dto.LoginResponse;
import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.repository.AdminUserRepository;
import com.radiostreaming.api.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Admin auth. Super-admin is seeded once from {@link CentralCredentialCatalog} into Mongo {@code admins}.
 * Password is never read from Railway env vars.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminUserRepository adminUserRepository;
    private final AdminDirectory adminDirectory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(
            AdminUserRepository adminUserRepository,
            AdminDirectory adminDirectory,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.adminUserRepository = adminUserRepository;
        this.adminDirectory = adminDirectory;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void seedAdminUser() {
        String username = CentralCredentialCatalog.ADMIN_USERNAME;
        adminUserRepository.findByUsername(username).ifPresentOrElse(existing -> {
            existing.setRole("SUPER_ADMIN");
            existing.setEnabled(true);
            existing.setOwnRecordsOnly(false);
            existing.setPermissions(AdminPermissions.fullAccess());
            adminDirectory.put(adminUserRepository.save(existing));
            log.info("Ensured super admin '{}' has full access (password unchanged; managed in Mongo)", username);
        }, () -> {
            AdminUser admin = new AdminUser();
            admin.setUsername(username);
            admin.setDisplayName("Super Admin");
            admin.setPasswordHash(passwordEncoder.encode(CentralCredentialCatalog.ADMIN_PASSWORD));
            admin.setRole("SUPER_ADMIN");
            admin.setEnabled(true);
            admin.setOwnRecordsOnly(false);
            admin.setPermissions(AdminPermissions.fullAccess());
            admin.setCreatedAt(Instant.now());
            admin.setCreatedBy("system");
            AdminUser saved = adminUserRepository.save(admin);
            adminDirectory.put(saved);
            log.info("Seeded super admin '{}' from central catalog (change password after first login)", username);
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
