package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.dto.LeadUpdateRequest;
import com.shivang.crm.modules.lead.entity.Lead;
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface LeadMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)

    @Mapping(target = "ownerId", source = "ownerUserId")
    Lead toEntity(LeadCreateRequest request);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "source", ignore = true)

    @Mapping(target = "ownerId", source = "ownerUserId")
    void updateEntity(
            LeadUpdateRequest request,
            @MappingTarget Lead lead);

    @Mapping(target = "ownerUserId", source = "ownerId")
    LeadResponse toResponse(Lead lead);

    List<LeadResponse> toResponseList(List<Lead> leads);
}