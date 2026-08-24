package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AdminUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentAdmin {

    private final AdminDirectory adminDirectory;

    public CurrentAdmin(AdminDirectory adminDirectory) {
        this.adminDirectory = adminDirectory;
    }

    public AdminUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        AdminUser user = adminDirectory.require(authentication.getName());
        if (!user.isEnabledAccount()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        return user;
    }
}
