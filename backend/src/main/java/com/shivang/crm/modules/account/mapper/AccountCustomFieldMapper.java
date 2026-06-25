package com.shivang.crm.modules.account.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.account.dto.AccountCustomFieldCreateRequest;
import com.shivang.crm.modules.account.dto.AccountCustomFieldResponse;
import com.shivang.crm.modules.account.entity.AccountCustomField;

@Mapper(componentModel = "spring")
public interface AccountCustomFieldMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountCustomField toEntity(AccountCustomFieldCreateRequest request);

    AccountCustomFieldResponse toResponse(AccountCustomField field);

    List<AccountCustomFieldResponse> toResponseList(List<AccountCustomField> fields);
}
