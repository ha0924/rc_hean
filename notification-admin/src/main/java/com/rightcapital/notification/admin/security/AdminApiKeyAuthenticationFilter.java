package com.rightcapital.notification.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
class AdminApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_API_KEY_HEADER = "X-Admin-Api-Key";

    private final AdminSecurityProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AdminApiKeyAuthenticationFilter(AdminSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getHeader(ADMIN_API_KEY_HEADER);
        String hash = properties.getApiKeyHash();
        if (key != null && !key.isBlank() && hash != null && !hash.isBlank() && passwordEncoder.matches(key, hash)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin-api-key", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")
            ));
        }
        filterChain.doFilter(request, response);
    }
}
