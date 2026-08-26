package com.shivang.crm.modules.rbac.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * RBAC-8: targeted eviction of PermissionEvaluatorService's "userPermissions"
 * cache after authorization-affecting mutations commit.
 *
 * Key format must mirror the @Cacheable SpEL key exactly:
 *   key = "#userId + ':' + #tenantId"   ->  "uuid:uuid" or "uuid:null"
 *
 * Eviction is registered AFTER COMMIT when inside a transaction, so a rolled
 * back mutation never invalidates still-valid entries; outside a transaction
 * it fires immediately. No global flushes.
 */
@Component
@RequiredArgsConstructor
public class PermissionCacheEvictor {

    private static final String CACHE_NAME = "userPermissions";

    private final CacheManager cacheManager;
    private final UserRoleRepository userRoleRepository;

    /** Evicts one user's cached permission context (both tenant and platform keys). */
    public void evictUserAfterCommit(UUID userId, UUID tenantId) {
        registerAfterCommit(() -> evict(userId, tenantId));
    }

    /** Resolves every user assigned the role and evicts each of their entries. */
    public void evictRoleUsersAfterCommit(UUID roleId) {
        List<UserRole> assignments = userRoleRepository.findByRoleId(roleId);
        if (assignments.isEmpty()) {
            return;
        }
        registerAfterCommit(() ->
                assignments.forEach(ur -> evict(ur.getUserId(), ur.getTenantId())));
    }

    private void evict(UUID userId, UUID tenantId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(userId + ":" + tenantId);
        }
    }

    private void registerAfterCommit(Runnable evictions) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictions.run();
                }
            });
        } else {
            evictions.run();
        }
    }
}
