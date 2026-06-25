package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.lead.dto.LeadCustomFieldCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadCustomFieldResponse;
import com.shivang.crm.modules.lead.entity.LeadCustomField;

@Mapper(componentModel = "spring")
public interface LeadCustomFieldMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)

    @Mapping(target = "optionsJson", source = "options")
    LeadCustomField toEntity(LeadCustomFieldCreateRequest request);


    @Mapping(target = "options", source = "optionsJson")
    LeadCustomFieldResponse toResponse(LeadCustomField field);

    List<LeadCustomFieldResponse> toResponseList(List<LeadCustomField> fields);

}
