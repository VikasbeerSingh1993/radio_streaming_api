package com.radiostreaming.api.security;

import com.radiostreaming.api.model.AdminUser;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import com.radiostreaming.api.saas.security.SaasPrincipal;
import com.radiostreaming.api.service.AdminDirectory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminDirectory adminDirectory;
    private final SaasUserRepository saasUserRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AdminDirectory adminDirectory,
            SaasUserRepository saasUserRepository) {
        this.jwtService = jwtService;
        this.adminDirectory = adminDirectory;
        this.saasUserRepository = saasUserRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    Claims claims = jwtService.parse(token);
                    String subject = claims.getSubject();
                    String role = claims.get("role", String.class);
                    if (role != null && role.startsWith("SAAS_")) {
                        authenticateSaas(subject, role);
                    } else {
                        authenticateAdmin(subject);
                    }
                } catch (JwtException | IllegalArgumentException ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateAdmin(String username) {
        AdminUser user = adminDirectory.find(username).orElse(null);
        if (user != null && user.isEnabledAccount()) {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            if (user.isSuperAdmin()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateSaas(String email, String role) {
        SaasUserDocument user = saasUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isEnabled()) {
            SecurityContextHolder.clearContext();
            return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SAAS_USER"));
        if ("SAAS_ADMIN".equalsIgnoreCase(user.getRole()) || "SAAS_ADMIN".equalsIgnoreCase(role)
                || "SAAS_SAAS_ADMIN".equalsIgnoreCase(role)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SAAS_ADMIN"));
        }
        SaasPrincipal principal = new SaasPrincipal(user, null, "jwt");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
