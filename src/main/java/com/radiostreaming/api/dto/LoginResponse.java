package com.radiostreaming.api.dto;

import com.radiostreaming.api.model.AdminUser;

public class LoginResponse {

    private String token;
    private long expiresInMs;
    private AdminProfile profile;

    public LoginResponse(String token, long expiresInMs, AdminUser user) {
        this.token = token;
        this.expiresInMs = expiresInMs;
        this.profile = AdminProfile.from(user);
    }

    public String getToken() {
        return token;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public AdminProfile getProfile() {
        return profile;
    }

    public String getUsername() {
        return profile == null ? null : profile.getUsername();
    }

    public String getRole() {
        return profile == null ? null : profile.getRole();
    }
}
