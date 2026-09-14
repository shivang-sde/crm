package com.shivang.crm.modules.records.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.records.dto.RecordMappingProfileResponse;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RecordMappingProfileMapper {

    @Mapping(target = "mode", expression = "java(entity.getMode() != null ? entity.getMode().name() : null)")
    RecordMappingProfileResponse toResponse(RecordMappingProfile entity);
}
