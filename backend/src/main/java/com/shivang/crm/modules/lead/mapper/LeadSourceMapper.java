package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.lead.dto.LeadSourceCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadSourceResponse;
import com.shivang.crm.modules.lead.entity.LeadSource;

@Mapper(componentModel = "spring")
public interface LeadSourceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    LeadSource toEntity(LeadSourceCreateRequest request);

    LeadSourceResponse toResponse(LeadSource source);

    List<LeadSourceResponse> toResponseList(List<LeadSource> sources);
}
