package com.radiostreaming.api.saas.dto;

public class SaasAccountSettingsRequest {
    private Boolean allowOcrOverage;
    private Boolean allowAiImageOverage;
    private String displayName;

    public Boolean getAllowOcrOverage() {
        return allowOcrOverage;
    }

    public void setAllowOcrOverage(Boolean allowOcrOverage) {
        this.allowOcrOverage = allowOcrOverage;
    }

    public Boolean getAllowAiImageOverage() {
        return allowAiImageOverage;
    }

    public void setAllowAiImageOverage(Boolean allowAiImageOverage) {
        this.allowAiImageOverage = allowAiImageOverage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
