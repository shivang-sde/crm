package com.shivang.crm.modules.entitlement.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementUpdateRequest;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerEntitlementMapper {

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "ownerUserId", source = "ownerId")
    CustomerEntitlementResponse toResponse(CustomerEntitlement entitlement);

    void updateEntity(CustomerEntitlementUpdateRequest request, @MappingTarget CustomerEntitlement entitlement);
}
