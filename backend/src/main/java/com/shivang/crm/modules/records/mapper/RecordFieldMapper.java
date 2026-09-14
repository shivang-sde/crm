package com.shivang.crm.modules.records.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.records.dto.RecordFieldResponse;
import com.shivang.crm.modules.records.entity.RecordField;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RecordFieldMapper {

    @Mapping(target = "options", source = "optionsJson")
    RecordFieldResponse toResponse(RecordField entity);
}
