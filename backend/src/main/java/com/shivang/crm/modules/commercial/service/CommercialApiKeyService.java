package com.shivang.crm.modules.commercial.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.commercial.entity.CommercialApiKey;
import com.shivang.crm.modules.commercial.repository.CommercialApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialApiKeyService {

    private final CommercialApiKeyRepository repository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;

    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String prefix(String raw) {
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    @Transactional
    public GeneratedKey generateOrRotate(UUID tenantId) {
        String rawKey = generateRawKey();
        String hash = sha256Hex(rawKey);
        String pfx = prefix(rawKey);

        CommercialApiKey existing = repository.findByTenantId(tenantId).orElse(null);
        if (existing != null) {
            existing.setKeyHash(hash);
            existing.setKeyPrefix(pfx);
            existing.setIsActive(true);
            repository.save(existing);
            log.info("Rotated commercial API key for tenant {}", tenantId);
        } else {
            CommercialApiKey key = CommercialApiKey.builder()
                .tenantId(tenantId)
                .keyHash(hash)
                .keyPrefix(pfx)
                .isActive(true)
                .build();
            repository.save(key);
            log.info("Generated commercial API key for tenant {}", tenantId);
        }
        return new GeneratedKey(rawKey, pfx);
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        // 32 bytes => 64 hex chars
        return HexFormat.of().formatHex(bytes);
    }

    @Transactional(readOnly = true)
    public Optional<CommercialApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        String hash = sha256Hex(rawKey.trim());
        // Direct hash lookup (prefix optimization optional)
        return repository.findByKeyHashAndIsActiveTrue(hash);
    }

    public record GeneratedKey(String rawKey, String prefix) {}
}
