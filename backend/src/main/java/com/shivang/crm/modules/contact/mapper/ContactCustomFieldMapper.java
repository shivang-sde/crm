package com.shivang.crm.modules.contact.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.contact.dto.ContactCustomFieldCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactCustomFieldResponse;
import com.shivang.crm.modules.contact.entity.ContactCustomField;

@Mapper(componentModel = "spring")
public interface ContactCustomFieldMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ContactCustomField toEntity(ContactCustomFieldCreateRequest request);

    ContactCustomFieldResponse toResponse(ContactCustomField field);

    List<ContactCustomFieldResponse> toResponseList(List<ContactCustomField> fields);
}
