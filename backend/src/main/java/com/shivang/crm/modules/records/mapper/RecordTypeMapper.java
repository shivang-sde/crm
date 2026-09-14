package com.shivang.crm.modules.records.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.records.dto.RecordTypeResponse;
import com.shivang.crm.modules.records.entity.RecordType;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RecordTypeMapper {

    RecordTypeResponse toResponse(RecordType entity);
}
