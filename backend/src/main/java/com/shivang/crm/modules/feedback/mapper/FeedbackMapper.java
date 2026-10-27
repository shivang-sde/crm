package com.shivang.crm.modules.feedback.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.entity.Feedback;

/**
 * L3.3-P1: Feedback to response.
 *
 * <p>L3.0-P5 contract: the DTO and the entity share camelCase field names, so this
 * mapper needs no renaming annotations. A {@code @Mapping} renaming here would be a
 * signal the wire contract had drifted.
 *
 * <p>{@code unmappedTargetPolicy = IGNORE} because the entity carries soft-delete
 * and audit columns the response deliberately does not expose.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeedbackMapper {

    FeedbackResponse toResponse(Feedback feedback);

    List<FeedbackResponse> toResponseList(List<Feedback> feedbacks);
}
