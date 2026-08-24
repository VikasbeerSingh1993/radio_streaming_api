package com.radiostreaming.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "admins")
public class AdminUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    /** SUPER_ADMIN or SUB_ADMIN. Legacy ADMIN is treated as SUPER_ADMIN. */
    private String role;

    private String displayName;

    @Indexed
    private String organization;

    private Boolean enabled;
    private Boolean ownRecordsOnly;
    private AdminPermissions permissions;
    private List<String> allowedCategoryKeys;
    private List<String> allowedOrganizations;
    private String createdBy;
    private Instant createdAt;
    private Instant lastLoginAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
        return allowedCategoryKeys == null ? List.of() : allowedCategoryKeys;
    }

    public void setAllowedCategoryKeys(List<String> allowedCategoryKeys) {
        this.allowedCategoryKeys = allowedCategoryKeys == null ? new ArrayList<>() : allowedCategoryKeys;
    }

    public List<String> getAllowedOrganizations() {
        return allowedOrganizations == null ? List.of() : allowedOrganizations;
    }

    public void setAllowedOrganizations(List<String> allowedOrganizations) {
        this.allowedOrganizations = allowedOrganizations == null ? new ArrayList<>() : allowedOrganizations;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    @JsonIgnore
    public boolean isEnabledAccount() {
        return enabled == null || enabled;
    }

    @JsonIgnore
    public boolean isSuperAdmin() {
        if (role == null || role.isBlank() || "ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    @JsonIgnore
    public boolean isOwnRecordsOnly() {
        return Boolean.TRUE.equals(ownRecordsOnly);
    }
}
