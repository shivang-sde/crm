package com.shivang.crm.modules.deal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DealMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "stage", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Deal toEntity(DealCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "stage", ignore = true)
    void updateEntity(DealUpdateRequest request, @MappingTarget Deal entity);

    @Mapping(target = "ownerUserId", source = "ownerId")
    @Mapping(target = "isWon", expression = "java(entity.isWon())")
    @Mapping(target = "isLost", expression = "java(entity.isLost())")
    @Mapping(target = "recordCategory", expression = "java(entity.getRecordCategory())")
    DealResponse toResponse(Deal entity);

    List<DealResponse> toResponseList(List<Deal> entities);
}
