package com.radiostreaming.api.saas.security;

import com.radiostreaming.api.saas.model.SaasUserDocument;

/**
 * Request-scoped SaaS principal resolved from JWT or API key.
 */
public class SaasPrincipal {

    private final SaasUserDocument user;
    private final String apiKeyId;
    private final String authMode;

    public SaasPrincipal(SaasUserDocument user, String apiKeyId, String authMode) {
        this.user = user;
        this.apiKeyId = apiKeyId;
        this.authMode = authMode;
    }

    public SaasUserDocument getUser() {
        return user;
    }

    public String getUserId() {
        return user.getId();
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public String getAuthMode() {
        return authMode;
    }

    public boolean isAdmin() {
        return "SAAS_ADMIN".equalsIgnoreCase(user.getRole());
    }
}
