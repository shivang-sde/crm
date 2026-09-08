package com.shivang.crm.modules.rbac.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Clears stale RBAC cache after SQL migrations that insert role_permissions directly.
 * Migrations like V12/V18 add call/task/meeting permissions to existing ADMIN roles
 * without going through RoleManagementService, so userPermissions cache for existing
 * tenants would remain without call:read until TTL (10m) or manual eviction.
 * This runner clears the cache once on startup (idempotent, cheap).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCacheInitializer implements CommandLineRunner {

    private final CacheManager cacheManager;

    @Override
    public void run(String... args) {
        try {
            var cache = cacheManager.getCache("userPermissions");
            if (cache != null) {
                cache.clear();
                log.info("Cleared stale userPermissions cache on startup (post-migration)");
            }
        } catch (Exception e) {
            log.warn("Failed to clear userPermissions cache on startup", e);
        }
    }
}
