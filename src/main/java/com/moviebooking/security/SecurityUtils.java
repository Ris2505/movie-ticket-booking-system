package com.moviebooking.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public UserPrincipal currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Not authenticated");
        }
        return principal;
    }

    public Long currentUserId() {
        return currentUser().getId();
    }
}
