package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.repository.AdminUserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminDirectory {

    private final AdminUserRepository adminUserRepository;
    private final ConcurrentHashMap<String, AdminUser> byUsername = new ConcurrentHashMap<>();

    public AdminDirectory(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void load() {
        reloadFromDatabase();
    }

    public void reloadFromDatabase() {
        byUsername.clear();
        for (AdminUser user : adminUserRepository.findAll()) {
            if (user.getUsername() != null) {
                byUsername.put(user.getUsername().toLowerCase(), user);
            }
        }
    }

    public void put(AdminUser user) {
        if (user.getUsername() != null) {
            byUsername.put(user.getUsername().toLowerCase(), user);
        }
    }

    public void remove(String username) {
        if (username != null) {
            byUsername.remove(username.toLowerCase());
        }
    }

    public Optional<AdminUser> find(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        AdminUser cached = byUsername.get(username.toLowerCase());
        if (cached != null) {
            return Optional.of(cached);
        }
        return adminUserRepository.findByUsername(username).map(user -> {
            put(user);
            return user;
        });
    }

    public AdminUser require(String username) {
        return find(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin account not found"));
    }

    public List<AdminUser> list() {
        return List.copyOf(byUsername.values());
    }
}
