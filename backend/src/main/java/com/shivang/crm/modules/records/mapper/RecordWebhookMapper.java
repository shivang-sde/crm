package com.shivang.crm.modules.records.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.records.dto.RecordWebhookResponse;
import com.shivang.crm.modules.records.entity.RecordWebhook;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RecordWebhookMapper {
    RecordWebhookResponse toResponse(RecordWebhook entity);
}
