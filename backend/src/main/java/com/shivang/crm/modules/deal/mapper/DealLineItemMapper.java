package com.shivang.crm.modules.deal.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemResponse;
import com.shivang.crm.modules.deal.entity.DealLineItem;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DealLineItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "dealId", ignore = true)
    @Mapping(target = "offeringId", ignore = true)
    @Mapping(target = "itemName", ignore = true)
    @Mapping(target = "itemCode", ignore = true)
    @Mapping(target = "lineTotal", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    DealLineItem toEntity(DealLineItemCreateRequest request);

    DealLineItemResponse toResponse(DealLineItem entity);

    List<DealLineItemResponse> toResponseList(List<DealLineItem> entities);
}
