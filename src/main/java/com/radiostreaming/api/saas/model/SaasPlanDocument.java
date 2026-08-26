package com.radiostreaming.api.saas.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaasPlanDocument {

    private String id;

    private String code;

    private String name;
    private String description;

    /** Price in minor units (paise for INR). */
    private int priceCents;

    /** ISO currency code; AI plans use INR. */
    private String priceCurrency = "INR";

    private long creditsIncluded;

    private int creditCostOcr = 5;

    private int creditCostAiImage = 10;

    private int creditCostSikhHistory = 2;

    private int creditCostGurbaniAi = 3;

    /** Max Sikh History questions per calendar day (hard max 100). */
    private int dailyLimitSikhHistory = 5;

    /** Max Gurbani AI voice searches per calendar day (hard max 100). */
    private int dailyLimitGurbaniAi = 5;

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

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(String priceCurrency) {
        this.priceCurrency = priceCurrency;
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

    public int getCreditCostGurbaniAi() {
        return creditCostGurbaniAi;
    }

    public void setCreditCostGurbaniAi(int creditCostGurbaniAi) {
        this.creditCostGurbaniAi = creditCostGurbaniAi;
    }

    public int getDailyLimitSikhHistory() {
        return dailyLimitSikhHistory;
    }

    public void setDailyLimitSikhHistory(int dailyLimitSikhHistory) {
        this.dailyLimitSikhHistory = dailyLimitSikhHistory;
    }

    public int getDailyLimitGurbaniAi() {
        return dailyLimitGurbaniAi;
    }

    public void setDailyLimitGurbaniAi(int dailyLimitGurbaniAi) {
        this.dailyLimitGurbaniAi = dailyLimitGurbaniAi;
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

