package com.shivang.crm.modules.auth.mapper;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.dto.response.TenantInfo;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.tenant.entity.Tenant;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantMapper {

    private final UserRepository userRepository;

    public TenantInfo toTenantInfo(Tenant tenant) {

        if (tenant == null) {
            return null;
        }

        return TenantInfo.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .maxUsers(tenant.getMaxUsers())
                .currentUsers(userRepository.countByTenantId(tenant.getId()))
                .planType(tenant.getPlanType())
                .isActive(tenant.getIsActive())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .subscriptionEndDate(tenant.getSubscriptionEndDate())
                .maxUsers(tenant.getMaxUsers())
                .build();
    }
}