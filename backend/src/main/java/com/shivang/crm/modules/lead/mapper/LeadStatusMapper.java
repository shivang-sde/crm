package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.lead.dto.LeadStatusCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadStatusResponse;
import com.shivang.crm.modules.lead.entity.LeadStatus;

@Mapper(componentModel = "spring")
public interface LeadStatusMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    LeadStatus toEntity(LeadStatusCreateRequest request);

    LeadStatusResponse toResponse(LeadStatus status);

    List<LeadStatusResponse> toResponseList(List<LeadStatus> statuses);
}
