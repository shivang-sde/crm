package com.shivang.crm.modules.tenant.service;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.tenant.dto.TenantResponseDTO;
import com.shivang.crm.modules.tenant.dto.TenantUpdateRequest;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.modules.auth.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public List<TenantResponseDTO> getAllTenants(UUID userId, String userRole) {

        log.info("userId={}", userId);
        log.info("userRole={}", userRole);
        log.info("tenantContext={}", tenantContext.getTenantId());

        List<Tenant> tenants;

        if ("SUPERADMIN".equals(userRole)) {
            log.info("SUPERADMIN branch");
            tenants = tenantRepository.findAllWithReseller();

        } else if ("RESELLER".equals(userRole)) {
            log.info("RESELLER branch");
            tenants = tenantRepository.findByResellerIdWithReseller(userId);

        } else {
            log.info("TENANT USER branch");

            String tenantIdStr = tenantContext.getTenantId();

            if (tenantIdStr != null) {
                UUID tenantId = UUID.fromString(tenantIdStr);
                Tenant tenant = tenantRepository.findById(tenantId)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "Tenant not found"));

                tenants = List.of(tenant);
            } else {
                log.warn("tenantId is null");
                tenants = List.of();
            }
        }

        log.info("tenant count={}", tenants.size());

        return tenants.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public TenantResponseDTO getTenant(UUID tenantId, UUID userId, String userRole) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Tenant not found"));

        // Check authorization
        if ("SUPERADMIN".equals(userRole)) {
            // SUPERADMIN can access any tenant
        } else if ("RESELLER".equals(userRole)) {
            // RESELLER can only access their own tenants
            if (!tenant.getResellerId().equals(userId)) {
                throw new BusinessException("FORBIDDEN", "You don't have access to this tenant");
            }
        } else {
            // Regular user can only access their own tenant
            String tenantIdStr = tenantContext.getTenantId();
            if (tenantIdStr == null || !tenantIdStr.equals(tenantId.toString())) {
                throw new BusinessException("FORBIDDEN", "You don't have access to this tenant");
            }
        }

        return mapToResponseDTO(tenant);
    }

    @Transactional
    public TenantResponseDTO updateTenant(
            UUID tenantId,
            TenantUpdateRequest request,
            UUID currentUserId,
            String role) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND",
                        "Tenant not found"));

        validateTenantAccess(
                tenant,
                currentUserId,
                role);

        tenant.setName(request.getCompanyName());
        tenant.setCompanyEmail(request.getCompanyEmail());
        tenant.setCompanyPhone(request.getCompanyPhone());
        tenant.setWebsite(request.getWebsite());

        tenant.setCountry(request.getCountry());
        tenant.setState(request.getState());
        tenant.setCity(request.getCity());
        tenant.setAddressLine1(request.getAddressLine1());
        tenant.setPostalCode(request.getPostalCode());

        tenant.setIndustry(request.getIndustry());
        tenant.setTimezone(request.getTimezone());
        tenant.setCurrencyCode(request.getCurrencyCode());
        tenant.setLanguage(request.getLanguage());

        tenant.setLogoUrl(request.getLogoUrl());
        tenant.setPrimaryColor(request.getPrimaryColor());

        tenant.setPlanType(request.getPlanType());
        tenant.setMaxUsers(request.getMaxUsers());

        tenant.setSubscriptionEndDate(
                request.getSubscriptionEndDate());

        if (request.getIsActive() != null) {
            tenant.setIsActive(request.getIsActive());
        }

        tenantRepository.save(tenant);

        return mapToResponseDTO(tenant);
    }

    private TenantResponseDTO mapToResponseDTO(Tenant tenant) {

        Integer currentUsers = userRepository.countByTenantId(tenant.getId());

        TenantResponseDTO.TenantResponseDTOBuilder builder = TenantResponseDTO.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .companyEmail(tenant.getCompanyEmail())
                .companyPhone(tenant.getCompanyPhone())
                .website(tenant.getWebsite())
                .slug(tenant.getSlug())
                .planType(tenant.getPlanType())
                .addressLine1(tenant.getAddressLine1())
                .city(tenant.getCity())
                .state(tenant.getState())
                .postalCode(tenant.getPostalCode())
                .country(tenant.getCountry())
                .timezone(tenant.getTimezone())
                .logoUrl(tenant.getLogoUrl())
                .currentUsers(currentUsers)
                .maxUsers(tenant.getMaxUsers())
                .subscriptionEndDate(tenant.getSubscriptionEndDate())
                .isActive(tenant.getIsActive())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt());

        if (tenant.getReseller() != null) {
            User reseller = tenant.getReseller();
            builder.reseller(TenantResponseDTO.ResellerInfo.builder()
                    .id(reseller.getId())
                    .email(reseller.getEmail())
                    .firstName(reseller.getFirstName())
                    .lastName(reseller.getLastName())
                    .build());
        }

        log.info("tenantResponseDTO===>" + builder.build().toString());

        return builder.build();
    }

    private void validateTenantAccess(
            Tenant tenant,
            UUID userId,
            String role) {

        if ("SUPERADMIN".equals(role)) {
            return;
        }

        if ("RESELLER".equals(role)) {

            if (!Objects.equals(
                    tenant.getResellerId(),
                    userId)) {

                throw new BusinessException(
                        "FORBIDDEN",
                        "You don't have access to this tenant");
            }

            return;
        }

        String tenantIdStr = tenantContext.getTenantId();

        if (tenantIdStr == null ||
                !tenantIdStr.equals(
                        tenant.getId().toString())) {

            throw new BusinessException(
                    "FORBIDDEN",
                    "You don't have access to this tenant");
        }
    }
}