package com.shivang.crm.modules.acquisition.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionSystemActorService {

    private static final String SYSTEM_EMAIL_TEMPLATE = "ingestion-system-%s@crm.local";
    private static final String SYSTEM_FIRST_NAME = "System";
    private static final String SYSTEM_LAST_NAME = "Ingestion";
    private static final String SYSTEM_PASSWORD_HASH = "system-internal-service-account";

    private final UserRepository userRepository;

    @Transactional
    public UUID ensureSystemActor(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException("VALIDATION_ERROR", "Tenant is required to resolve the ingestion system actor");
        }

        String email = String.format(SYSTEM_EMAIL_TEMPLATE, tenantId.toString().replace("-", ""));
        return userRepository.findByTenantIdAndEmail(tenantId, email)
            .map(User::getId)
            .orElseGet(() -> {
                User systemActor = User.builder()
                    .tenantId(tenantId)
                    .email(email)
                    .passwordHash(SYSTEM_PASSWORD_HASH)
                    .firstName(SYSTEM_FIRST_NAME)
                    .lastName(SYSTEM_LAST_NAME)
                    .isActive(false)
                    .emailVerified(false)
                    .roleId(null)
                    .build();

                User saved = userRepository.save(systemActor);
                log.info("Created tenant-scoped system actor {} for ingestion in tenant {}", saved.getId(), tenantId);
                return saved.getId();
            });
    }
}
