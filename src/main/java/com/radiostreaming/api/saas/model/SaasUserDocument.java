package com.radiostreaming.api.saas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * SaaS account user stored in Mongo (not MySQL bani_search).
 */
@Document(collection = "saas_users")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaasUserDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String displayName;

    @JsonIgnore
    private String passwordHash;

    /** USER or SAAS_ADMIN */
    private String role = "USER";

    private boolean enabled = true;

    private String planId;

    private String planName;

    @Field("credits_remaining")
    private long creditsRemaining;

    @Field("credits_used")
    private long creditsUsed;

    @Field("credits_pending")
    private long creditsPending;

    @Field("allow_ocr_overage")
    private boolean allowOcrOverage;

    @Field("allow_ai_image_overage")
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
