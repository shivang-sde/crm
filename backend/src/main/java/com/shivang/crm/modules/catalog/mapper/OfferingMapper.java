package com.shivang.crm.modules.catalog.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingResponse;
import com.shivang.crm.modules.catalog.dto.OfferingUpdateRequest;
import com.shivang.crm.modules.catalog.entity.Offering;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OfferingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "ownerId", source = "ownerUserId")
    Offering toEntity(OfferingCreateRequest request);

    @Mapping(target = "ownerId", source = "ownerUserId")
    void updateEntity(OfferingUpdateRequest request, @MappingTarget Offering offering);

    @Mapping(target = "ownerUserId", source = "ownerId")
    OfferingResponse toResponse(Offering offering);

    List<OfferingResponse> toResponseList(List<Offering> offerings);
}
