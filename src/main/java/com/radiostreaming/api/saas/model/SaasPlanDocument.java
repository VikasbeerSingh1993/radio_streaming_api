package com.radiostreaming.api.saas.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "saas_plans")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaasPlanDocument {

    @Id
    private String id;

    @Indexed
    private String code;

    private String name;
    private String description;

    /** Price in USD cents */
    private int priceCents;

    private long creditsIncluded;

    @Field("credit_cost_ocr")
    private int creditCostOcr = 5;

    @Field("credit_cost_ai_image")
    private int creditCostAiImage = 10;

    @Field("credit_cost_sikh_history")
    private int creditCostSikhHistory = 2;

    private List<String> features = new ArrayList<>();

    private boolean active = true;

    private int sortOrder;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(int priceCents) {
        this.priceCents = priceCents;
    }

    public long getCreditsIncluded() {
        return creditsIncluded;
    }

    public void setCreditsIncluded(long creditsIncluded) {
        this.creditsIncluded = creditsIncluded;
    }

    public int getCreditCostOcr() {
        return creditCostOcr;
    }

    public void setCreditCostOcr(int creditCostOcr) {
        this.creditCostOcr = creditCostOcr;
    }

    public int getCreditCostAiImage() {
        return creditCostAiImage;
    }

    public void setCreditCostAiImage(int creditCostAiImage) {
        this.creditCostAiImage = creditCostAiImage;
    }

    public int getCreditCostSikhHistory() {
        return creditCostSikhHistory;
    }

    public void setCreditCostSikhHistory(int creditCostSikhHistory) {
        this.creditCostSikhHistory = creditCostSikhHistory;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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
