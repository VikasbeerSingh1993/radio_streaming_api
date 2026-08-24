package com.radiostreaming.api.dto;

import com.radiostreaming.api.model.AdminPermissions;
import com.radiostreaming.api.model.AdminUser;

import java.time.Instant;
import java.util.List;

public class AdminProfile {

    private String id;
    private String username;
    private String displayName;
    private String role;
    private String organization;
    private boolean superAdmin;
    private boolean enabled;
    private boolean ownRecordsOnly;
    private AdminPermissions permissions;
    private List<String> allowedCategoryKeys;
    private List<String> allowedOrganizations;
    private Instant lastLoginAt;

    public static AdminProfile from(AdminUser user) {
        AdminProfile profile = new AdminProfile();
        profile.id = user.getId();
        profile.username = user.getUsername();
        profile.displayName = user.getDisplayName();
        profile.role = user.isSuperAdmin() ? "SUPER_ADMIN" : "SUB_ADMIN";
        profile.organization = user.getOrganization();
        profile.superAdmin = user.isSuperAdmin();
        profile.enabled = user.isEnabledAccount();
        profile.ownRecordsOnly = user.isOwnRecordsOnly();
        profile.permissions = user.isSuperAdmin()
                ? AdminPermissions.fullAccess()
                : (user.getPermissions() == null ? AdminPermissions.none() : user.getPermissions());
        profile.allowedCategoryKeys = user.getAllowedCategoryKeys();
        profile.allowedOrganizations = user.getAllowedOrganizations();
        profile.lastLoginAt = user.getLastLoginAt();
        return profile;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getOrganization() {
        return organization;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOwnRecordsOnly() {
        return ownRecordsOnly;
    }

    public AdminPermissions getPermissions() {
        return permissions;
    }

    public List<String> getAllowedCategoryKeys() {
        return allowedCategoryKeys;
    }

    public List<String> getAllowedOrganizations() {
        return allowedOrganizations;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
