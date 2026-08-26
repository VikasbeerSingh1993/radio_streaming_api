package com.radiostreaming.api.saas.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "saas_usage_events")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaasUsageEventDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String apiKeyId;

    /** OCR_PUNJABI, AI_IMAGE, etc. */
    @Indexed
    private String operation;

    private int creditsCharged;

    private boolean overage;

    private String status;

    private Map<String, Object> metadata;

    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(String apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getCreditsCharged() {
        return creditsCharged;
    }

    public void setCreditsCharged(int creditsCharged) {
        this.creditsCharged = creditsCharged;
    }

    public boolean isOverage() {
        return overage;
    }

    public void setOverage(boolean overage) {
        this.overage = overage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
