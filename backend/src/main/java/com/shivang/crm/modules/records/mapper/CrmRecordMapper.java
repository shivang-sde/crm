package com.shivang.crm.modules.records.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.records.dto.CrmRecordResponse;
import com.shivang.crm.modules.records.entity.CrmRecord;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CrmRecordMapper {

    CrmRecordResponse toResponse(CrmRecord entity);
}
