package com.shivang.crm.util;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserUtil {
    public static String getUserRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                // Authorities carry the Spring "ROLE_" prefix; every caller
                // compares against bare catalog role names (SUPERADMIN, ...).
                .map(authority -> authority.startsWith("ROLE_")
                        ? authority.substring("ROLE_".length())
                        : authority)
                .orElse("EMPLOYEE");
    }

    public static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
