package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.AdminProfile;
import com.radiostreaming.api.dto.AdminUserRequest;
import com.radiostreaming.api.dto.PageResponse;
import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.repository.AdminUserRepository;
import com.radiostreaming.api.security.AdminAction;
import com.radiostreaming.api.security.AdminModule;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final AdminDirectory adminDirectory;
    private final AdminAccessService accessService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AdminUserRepository adminUserRepository,
            AdminDirectory adminDirectory,
            AdminAccessService accessService,
            PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.adminDirectory = adminDirectory;
        this.accessService = accessService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminProfile> list(AdminUser actor, String query) {
        accessService.assertCan(actor, AdminModule.USERS, AdminAction.READ);
        return accessService.visibleUsers(actor, adminDirectory.list(), query).stream()
                .map(AdminProfile::from)
                .toList();
    }

    public PageResponse<AdminProfile> list(AdminUser actor, String query, int page, int size) {
        return PageResponse.of(list(actor, query), page, size);
    }

    public AdminProfile create(AdminUser actor, AdminUserRequest request) {
        accessService.assertCan(actor, AdminModule.USERS, AdminAction.CREATE);
        String username = request.getUsername().trim();
        if (adminUserRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        AdminUser user = new AdminUser();
        apply(actor, user, request, true);
        user.setCreatedAt(Instant.now());
        user.setCreatedBy(actor.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        AdminUser saved = adminUserRepository.save(user);
        adminDirectory.put(saved);
        return AdminProfile.from(saved);
    }

    public AdminProfile update(AdminUser actor, String id, AdminUserRequest request) {
        accessService.assertCan(actor, AdminModule.USERS, AdminAction.UPDATE);
        AdminUser existing = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        apply(actor, existing, request, false);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
            }
            existing.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        AdminUser saved = adminUserRepository.save(existing);
        adminDirectory.put(saved);
        return AdminProfile.from(saved);
    }

    public void delete(AdminUser actor, String id) {
        accessService.assertCan(actor, AdminModule.USERS, AdminAction.DELETE);
        AdminUser existing = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        if (existing.getUsername().equalsIgnoreCase(actor.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }
        if (existing.isSuperAdmin() && superAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last super admin");
        }
        adminUserRepository.deleteById(id);
        adminDirectory.remove(existing.getUsername());
    }

    private void apply(AdminUser actor, AdminUser target, AdminUserRequest request, boolean creating) {
        target.setUsername(request.getUsername().trim());
        target.setDisplayName(trimToNull(request.getDisplayName()));
        target.setOrganization(trimToNull(request.getOrganization()));
        target.setEnabled(request.getEnabled() == null || request.getEnabled());
        target.setOwnRecordsOnly(Boolean.TRUE.equals(request.getOwnRecordsOnly()));
        target.setAllowedCategoryKeys(request.getAllowedCategoryKeys());
        target.setAllowedOrganizations(request.getAllowedOrganizations());

        boolean grantSuper = request.getRole() != null
                && "SUPER_ADMIN".equalsIgnoreCase(request.getRole().trim());
        if (grantSuper && !actor.isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super admin can grant super admin access");
        }
        if (grantSuper) {
            target.setRole("SUPER_ADMIN");
            target.setPermissions(AdminPermissions.fullAccess());
            target.setOwnRecordsOnly(false);
            target.setAllowedCategoryKeys(List.of());
            target.setAllowedOrganizations(List.of());
        } else {
            if (!creating && target.isSuperAdmin() && superAdminCount() <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote the last super admin");
            }
            target.setRole("SUB_ADMIN");
            target.setPermissions(request.getPermissions() == null
                    ? AdminPermissions.none()
                    : request.getPermissions());
        }
    }

    private long superAdminCount() {
        return adminDirectory.list().stream().filter(AdminUser::isSuperAdmin).count();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String normalizeOrg(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
