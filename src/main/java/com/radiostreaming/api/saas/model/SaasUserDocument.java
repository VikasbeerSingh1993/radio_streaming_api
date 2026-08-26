package com.radiostreaming.api.saas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * SaaS account user stored in MySQL {@code divine_bliss_web}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaasUserDocument {

    private String id;

    private String email;

    private String displayName;

    @JsonIgnore
    private String passwordHash;

    /** USER or SAAS_ADMIN */
    private String role = "USER";

    private boolean enabled = true;

    private String planId;

    private String planName;

    private long creditsRemaining;

    private long creditsUsed;

    private long creditsPending;

    private boolean allowOcrOverage;

    private boolean allowAiImageOverage;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public long getCreditsRemaining() {
        return creditsRemaining;
    }

    public void setCreditsRemaining(long creditsRemaining) {
        this.creditsRemaining = creditsRemaining;
    }

    public long getCreditsUsed() {
        return creditsUsed;
    }

    public void setCreditsUsed(long creditsUsed) {
        this.creditsUsed = creditsUsed;
    }

    public long getCreditsPending() {
        return creditsPending;
    }

    public void setCreditsPending(long creditsPending) {
        this.creditsPending = creditsPending;
    }

    public boolean isAllowOcrOverage() {
        return allowOcrOverage;
    }

    public void setAllowOcrOverage(boolean allowOcrOverage) {
        this.allowOcrOverage = allowOcrOverage;
    }

    public boolean isAllowAiImageOverage() {
        return allowAiImageOverage;
    }

    public void setAllowAiImageOverage(boolean allowAiImageOverage) {
        this.allowAiImageOverage = allowAiImageOverage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
