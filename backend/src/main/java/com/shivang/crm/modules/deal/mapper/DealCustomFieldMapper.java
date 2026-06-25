package com.shivang.crm.modules.deal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.deal.dto.DealCustomFieldCreateRequest;
import com.shivang.crm.modules.deal.dto.DealCustomFieldResponse;
import com.shivang.crm.modules.deal.entity.DealCustomField;

@Mapper(componentModel = "spring")
public interface DealCustomFieldMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "optionsJson", source = "options")
    DealCustomField toEntity(DealCustomFieldCreateRequest request);

    @Mapping(target = "options", source = "optionsJson")
    DealCustomFieldResponse toResponse(DealCustomField field);

    List<DealCustomFieldResponse> toResponseList(List<DealCustomField> fields);
}
