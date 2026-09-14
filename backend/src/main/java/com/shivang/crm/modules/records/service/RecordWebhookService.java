package com.shivang.crm.modules.records.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.service.CredentialEncryptionService;
import com.shivang.crm.modules.records.dto.RecordWebhookCreateRequest;
import com.shivang.crm.modules.records.dto.RecordWebhookResponse;
import com.shivang.crm.modules.records.dto.RecordWebhookUpdateRequest;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.WebhookAuthMode;
import com.shivang.crm.modules.records.mapper.RecordWebhookMapper;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordWebhookService {

    private final RecordWebhookRepository webhookRepo;
    private final RecordTypeRepository recordTypeRepo;
    private final RecordMappingProfileRepository mappingRepo;
    private final RecordWebhookMapper mapper;
    private final CredentialEncryptionService encryptionService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RecordWebhookResponse create(UUID tenantId, UUID userId, RecordWebhookCreateRequest req) {
        String key = req.getWebhookKey().trim().toLowerCase();
        if (!key.matches("^[a-z][a-z0-9_-]*$") || key.length() < 3) {
            throw new BusinessException("INVALID_WEBHOOK_KEY", "Invalid webhookKey");
        }
        if (webhookRepo.existsByTenantIdAndWebhookKeyAndDeletedFalse(tenantId, key)) {
            throw new BusinessException("DUPLICATE_WEBHOOK_KEY", "Webhook key '" + key + "' already exists");
        }

        RecordType rt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(req.getRecordTypeId(), tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", req.getRecordTypeId().toString()));
        if (Boolean.FALSE.equals(rt.getIsActive())) {
            throw new BusinessException("RECORD_TYPE_INACTIVE", "RecordType is inactive");
        }

        UUID mappingId = req.getMappingProfileId();
        if (mappingId != null) {
            RecordMappingProfile mp = mappingRepo.findByIdAndTenantIdAndDeletedFalse(mappingId, tenantId)
                    .orElseThrow(() -> new NotFoundException("RecordMappingProfile", mappingId.toString()));
            if (!mp.getRecordTypeId().equals(req.getRecordTypeId())) {
                throw new BusinessException("MAPPING_RECORDTYPE_MISMATCH", "Mapping profile does not belong to the selected RecordType");
            }
            if (Boolean.FALSE.equals(mp.getIsActive())) {
                throw new BusinessException("MAPPING_INACTIVE", "Mapping profile is inactive");
            }
        }

        WebhookAuthMode authMode = parseAuthMode(req.getAuthMode());

        RecordWebhook entity = RecordWebhook.builder()
                .tenantId(tenantId)
                .createdBy(userId)
                .ownerId(userId)
                .name(req.getName().trim())
                .webhookKey(key)
                .description(req.getDescription())
                .recordTypeId(req.getRecordTypeId())
                .mappingProfileId(mappingId)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .authMode(authMode)
                .build();

        RecordWebhook saved = webhookRepo.save(entity);
        log.info("Created webhook {} tenant {} authMode {}", saved.getId(), tenantId, authMode);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecordWebhookResponse get(UUID tenantId, UUID id) {
        RecordWebhook e = webhookRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", id.toString()));
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public Page<RecordWebhookResponse> list(UUID tenantId, UUID recordTypeId, Boolean isActive, int page, int size) {
        Page<RecordWebhook> p;
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (recordTypeId != null) {
            recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                    .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
            p = webhookRepo.findByTenantIdAndRecordTypeIdAndDeletedFalse(tenantId, recordTypeId, pr);
        } else if (isActive != null) {
            p = webhookRepo.findByTenantIdAndIsActiveAndDeletedFalse(tenantId, isActive, pr);
        } else {
            p = webhookRepo.findByTenantIdAndDeletedFalse(tenantId, pr);
        }
        return p.map(this::toResponse);
    }

    public RecordWebhookResponse update(UUID tenantId, UUID id, RecordWebhookUpdateRequest req) {
        RecordWebhook entity = webhookRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", id.toString()));

        if (req.getName() != null) {
            if (req.getName().isBlank()) throw new BusinessException("INVALID_NAME", "Name cannot be blank");
            entity.setName(req.getName().trim());
        }
        if (req.getDescription() != null) entity.setDescription(req.getDescription());

        if (req.getRecordTypeId() != null && !req.getRecordTypeId().equals(entity.getRecordTypeId())) {
            RecordType rt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(req.getRecordTypeId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("RecordType", req.getRecordTypeId().toString()));
            if (Boolean.FALSE.equals(rt.getIsActive())) throw new BusinessException("RECORD_TYPE_INACTIVE", "RecordType is inactive");
            entity.setRecordTypeId(req.getRecordTypeId());
            if (entity.getMappingProfileId() != null) {
                var mpOpt = mappingRepo.findByIdAndTenantIdAndDeletedFalse(entity.getMappingProfileId(), tenantId);
                if (mpOpt.isEmpty() || !mpOpt.get().getRecordTypeId().equals(req.getRecordTypeId())) {
                    entity.setMappingProfileId(null);
                }
            }
        }

        if (req.isMappingProfileIdPresent()) {
            UUID newMappingId = req.getMappingProfileId();
            if (newMappingId == null) {
                entity.setMappingProfileId(null);
            } else {
                RecordMappingProfile mp = mappingRepo.findByIdAndTenantIdAndDeletedFalse(newMappingId, tenantId)
                        .orElseThrow(() -> new NotFoundException("RecordMappingProfile", newMappingId.toString()));
                if (!mp.getRecordTypeId().equals(entity.getRecordTypeId())) {
                    throw new BusinessException("MAPPING_RECORDTYPE_MISMATCH", "Mapping profile does not belong to webhook's RecordType");
                }
                if (Boolean.FALSE.equals(mp.getIsActive())) throw new BusinessException("MAPPING_INACTIVE", "Mapping profile is inactive");
                entity.setMappingProfileId(newMappingId);
            }
        }

        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());

        if (req.getAuthMode() != null) {
            WebhookAuthMode newMode = parseAuthMode(req.getAuthMode());
            if (newMode != entity.getAuthMode()) {
                entity.setAuthMode(newMode);
                // Clear old secrets when mode changes – require rotation
                entity.setSecretHash(null);
                entity.setSecretEncrypted(null);
                log.info("Webhook {} authMode changed to {} – secrets cleared, rotation required", id, newMode);
            }
        }

        entity.setUpdatedBy(com.shivang.crm.util.UserUtil.currentUserId());
        RecordWebhook saved = webhookRepo.save(entity);
        return toResponse(saved);
    }

    public RecordWebhookResponse clearMapping(UUID tenantId, UUID id) {
        RecordWebhook entity = webhookRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", id.toString()));
        entity.setMappingProfileId(null);
        entity.setUpdatedBy(com.shivang.crm.util.UserUtil.currentUserId());
        return toResponse(webhookRepo.save(entity));
    }

    public void delete(UUID tenantId, UUID id, UUID userId) {
        RecordWebhook entity = webhookRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", id.toString()));
        entity.softDelete(userId);
        webhookRepo.save(entity);
        log.info("Soft-deleted webhook {} tenant {}", id, tenantId);
    }

    // ===== Secret handling =====

    public record RotateSecretResult(String plainSecret, RecordWebhookResponse webhook) {}

    public RotateSecretResult rotateSecret(UUID tenantId, UUID webhookId) {
        RecordWebhook entity = webhookRepo.findByIdAndTenantIdAndDeletedFalse(webhookId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", webhookId.toString()));

        WebhookAuthMode mode = entity.getAuthMode();
        if (mode == WebhookAuthMode.NONE) {
            throw new BusinessException("INVALID_AUTH_MODE", "Cannot rotate secret for NONE auth mode. Change auth mode first.");
        }

        String plainSecret = generateSecret();
        if (mode == WebhookAuthMode.API_KEY) {
            String hash = hashSecret(plainSecret);
            entity.setSecretHash(hash);
            entity.setSecretEncrypted(null);
        } else if (mode == WebhookAuthMode.HMAC_SHA256) {
            String encrypted = encryptionService.encrypt(plainSecret);
            entity.setSecretEncrypted(encrypted);
            // Also store hash for non-reversible lookup? Not needed, but keep hash for audit? We store hash as well for HMAC? No, store encrypted only.
            entity.setSecretHash(null);
        }

        entity.setUpdatedBy(com.shivang.crm.util.UserUtil.currentUserId());
        RecordWebhook saved = webhookRepo.save(entity);
        // Never log plain secret
        log.info("Rotated secret for webhook {} tenant {} mode {}", webhookId, tenantId, mode);
        return new RotateSecretResult(plainSecret, toResponse(saved));
    }

    public boolean verifyApiKey(RecordWebhook webhook, String providedKey) {
        if (webhook.getSecretHash() == null || providedKey == null) return false;
        String providedHash = hashSecret(providedKey);
        return constantTimeEquals(providedHash, webhook.getSecretHash());
    }

    public boolean verifyHmac(RecordWebhook webhook, String payload, String providedSignature) {
        if (webhook.getSecretEncrypted() == null || providedSignature == null || payload == null) return false;
        String secret;
        try {
            secret = encryptionService.decrypt(webhook.getSecretEncrypted());
        } catch (Exception e) {
            log.warn("Failed to decrypt webhook secret for {}", webhook.getId());
            return false;
        }
        String computed = hmacSha256Hex(secret, payload);
        return constantTimeEquals(computed, providedSignature.trim().toLowerCase());
    }

    // Public ingress helper - used by RecordWebhookIngressController
    @Transactional(readOnly = true)
    public RecordWebhook findForIngress(String webhookKey) {
        // Find active, not deleted webhook by key (global lookup, but webhookKey is tenant-unique, we must find single)
        // Use repository with webhookKey only – we add method findByWebhookKeyAndDeletedFalseAndIsActiveTrue
        // For now, iterate over findByWebhookKey – we add a custom query method.
        // Fallback: use existing tenant-scoped? For ingress we don't have tenant, so we need global lookup.
        // We will query via webhookRepo.findByWebhookKeyAndDeletedFalse
        return webhookRepo.findByWebhookKeyAndDeletedFalseAndIsActiveTrue(webhookKey).orElse(null);
    }

    public static String hashSecret(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash secret", e);
        }
    }

    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmac) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    public static String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private WebhookAuthMode parseAuthMode(String raw) {
        if (raw == null || raw.isBlank()) return WebhookAuthMode.NONE;
        try { return WebhookAuthMode.fromString(raw); }
        catch (Exception e) { throw new BusinessException("INVALID_AUTH_MODE", "Invalid authMode '" + raw + "'. Allowed: NONE, API_KEY, HMAC_SHA256"); }
    }

    private RecordWebhookResponse toResponse(RecordWebhook e) {
        RecordWebhookResponse r = mapper.toResponse(e);
        r.setAuthMode(e.getAuthMode() != null ? e.getAuthMode().name() : WebhookAuthMode.NONE.name());
        var rt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(e.getRecordTypeId(), e.getTenantId()).orElse(null);
        if (rt != null) {
            r.setRecordTypeKey(rt.getKey());
            r.setRecordTypeName(rt.getName());
        }
        if (e.getMappingProfileId() != null) {
            var mp = mappingRepo.findByIdAndTenantIdAndDeletedFalse(e.getMappingProfileId(), e.getTenantId()).orElse(null);
            if (mp != null) {
                r.setMappingKey(mp.getMappingKey());
                r.setMappingName(mp.getName());
                r.setMappingMode(mp.getMode().name());
            }
        }
        r.setEndpointPath("/api/v1/records/webhooks/" + e.getWebhookKey());
        return r;
    }
}
