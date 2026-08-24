package com.radiostreaming.api.dto;

import com.radiostreaming.api.model.AdminPermissions;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class AdminUserRequest {

    @NotBlank
    private String username;
    private String password;
    private String displayName;
    private String role;
    private String organization;
    private Boolean enabled;
    private Boolean ownRecordsOnly;
    private AdminPermissions permissions;
    private List<String> allowedCategoryKeys = new ArrayList<>();
    private List<String> allowedOrganizations = new ArrayList<>();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getOwnRecordsOnly() {
        return ownRecordsOnly;
    }

    public void setOwnRecordsOnly(Boolean ownRecordsOnly) {
        this.ownRecordsOnly = ownRecordsOnly;
    }

    public AdminPermissions getPermissions() {
        return permissions;
    }

    public void setPermissions(AdminPermissions permissions) {
        this.permissions = permissions;
    }

    public List<String> getAllowedCategoryKeys() {
        return allowedCategoryKeys;
    }

    public void setAllowedCategoryKeys(List<String> allowedCategoryKeys) {
        this.allowedCategoryKeys = allowedCategoryKeys;
    }

    public List<String> getAllowedOrganizations() {
        return allowedOrganizations;
    }

    public void setAllowedOrganizations(List<String> allowedOrganizations) {
        this.allowedOrganizations = allowedOrganizations;
    }
}
