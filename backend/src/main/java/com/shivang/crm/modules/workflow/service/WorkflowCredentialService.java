package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionCredential;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionCredentialRepository;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Generic HTTP credential service for CREDENTIAL templating mode.
 * Reuses OutboundHttpConnectionCredential with connectionId IS NULL.
 * Supports TENANT (ownerUserId null, scope TENANT) and USER (ownerUserId, scope USER).
 * Credentials are arbitrary JSON maps, encrypted at rest, never returned via API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowCredentialService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final OutboundHttpConnectionCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findGenericCredential(UUID tenantId, UUID ownerUserId, String scope) {
        try {
            String normalizedScope = scope == null ? (ownerUserId == null ? "TENANT" : "USER") : scope.toUpperCase();
            if ("TENANT".equals(normalizedScope)) {
                var list = credentialRepository.findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, "TENANT");
                if (list.isEmpty()) return Optional.empty();
                return Optional.of(decryptToMap(list.get(0)));
            } else {
                if (ownerUserId == null) return Optional.empty();
                var list = credentialRepository.findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, ownerUserId);
                if (list.isEmpty()) return Optional.empty();
                // Prefer most recent USER row
                return Optional.of(decryptToMap(list.get(0)));
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve generic credential tenant={} owner={} scope={}: {}", tenantId, ownerUserId, scope, ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> resolveForUserOrTenant(UUID tenantId, UUID userId, String credentialSource) {
        String source = credentialSource == null ? "WORKFLOW_USER" : credentialSource.trim().toUpperCase();
        switch (source) {
            case "TENANT":
                return findGenericCredential(tenantId, null, "TENANT");
            case "WORKFLOW_USER":
            case "RECORD_OWNER":
            case "SPECIFIC_USER":
                if (userId == null) return Optional.empty();
                // Try USER first, then no fallback — explicit semantics per WF-26 Phase 8
                return findGenericCredential(tenantId, userId, "USER");
            default:
                return Optional.empty();
        }
    }

    @Transactional
    public OutboundHttpConnectionCredential storeGenericCredential(UUID tenantId, UUID actorId, UUID ownerUserId, String scope, Map<String, Object> values, String authType) {
        String normalizedScope = scope == null ? (ownerUserId == null ? "TENANT" : "USER") : scope.toUpperCase();
        if ("USER".equals(normalizedScope) && ownerUserId != null) {
            var userOpt = userRepository.findByIdAndTenantIdAndDeletedFalse(ownerUserId, tenantId);
            if (userOpt.isEmpty() || Boolean.FALSE.equals(userOpt.get().getIsActive()) || userOpt.get().isDeleted()) {
                throw new BusinessException("VALIDATION_ERROR", "Selected user does not belong to this tenant or is inactive");
            }
        }
        // Deactivate existing active rows for same tenant+owner+scope
        if ("TENANT".equals(normalizedScope)) {
            credentialRepository.findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, "TENANT")
                .forEach(c -> { c.setIsActive(false); c.softDelete(actorId); credentialRepository.save(c); });
        } else if (ownerUserId != null) {
            credentialRepository.findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, ownerUserId)
                .forEach(c -> { c.setIsActive(false); c.softDelete(actorId); credentialRepository.save(c); });
        }
        try {
            String encrypted = encryptionService.encrypt(objectMapper.writeValueAsString(values));
            OutboundHttpConnectionCredential credential = OutboundHttpConnectionCredential.builder()
                .tenantId(tenantId)
                .authType(authType == null ? "CREDENTIAL" : authType)
                .encryptedValue(encrypted)
                .connectionId(null)
                .ownerUserId(ownerUserId)
                .credentialScope(normalizedScope)
                .isActive(true)
                .build();
            credential.setDeleted(false);
            return credentialRepository.save(credential);
        } catch (Exception ex) {
            throw new com.shivang.crm.shared.exception.BusinessException("CREDENTIAL_STORE_FAILED", "Credential could not be stored securely");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasGenericCredential(UUID tenantId, UUID ownerUserId, String scope) {
        String normalizedScope = scope == null ? (ownerUserId == null ? "TENANT" : "USER") : scope.toUpperCase();
        if ("TENANT".equals(normalizedScope)) {
            return !credentialRepository.findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, "TENANT").isEmpty();
        } else {
            if (ownerUserId == null) return false;
            return !credentialRepository.findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, ownerUserId).isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<String> getCredentialKeys(UUID tenantId, UUID ownerUserId, String scope) {
        var opt = findGenericCredential(tenantId, ownerUserId, scope);
        if (opt.isEmpty()) return java.util.List.of();
        return new java.util.ArrayList<>(opt.get().keySet().stream().map(String::valueOf).sorted().toList());
    }

    @Transactional
    public void deleteGenericCredential(UUID tenantId, UUID actorId, UUID ownerUserId, String scope) {
        String normalizedScope = scope == null ? (ownerUserId == null ? "TENANT" : "USER") : scope.toUpperCase();
        if ("TENANT".equals(normalizedScope)) {
            credentialRepository.findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, "TENANT")
                .forEach(c -> { c.setIsActive(false); c.softDelete(actorId); credentialRepository.save(c); });
        } else {
            if (ownerUserId == null) return;
            credentialRepository.findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(tenantId, ownerUserId)
                .forEach(c -> { c.setIsActive(false); c.softDelete(actorId); credentialRepository.save(c); });
        }
    }

    private Map<String, Object> decryptToMap(OutboundHttpConnectionCredential credential) throws Exception {
        String decrypted = encryptionService.decrypt(credential.getEncryptedValue());
        return objectMapper.readValue(decrypted, MAP_TYPE);
    }
}
