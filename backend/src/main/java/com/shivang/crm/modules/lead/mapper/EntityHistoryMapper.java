package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.shivang.crm.modules.lead.dto.EntityHistoryResponse;
import com.shivang.crm.modules.lead.entity.EntityHistory;

@Mapper(componentModel = "spring")
public interface EntityHistoryMapper {

    EntityHistoryResponse toResponse(EntityHistory history);

    List<EntityHistoryResponse> toResponseList(List<EntityHistory> histories);

    
}
