package com.shivang.crm.modules.ticket.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.ticket.dto.TicketMessageResponse;
import com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest;
import com.shivang.crm.modules.ticket.entity.TicketMessage;

/**
 * L3.1-P3: TicketMessage mapping.
 *
 * <p>Same shape as {@code TicketMapper}: the DTOs and the entity share camelCase
 * names, so there is no renaming annotation here by design — one appearing later
 * would be a signal the wire contract had drifted.
 *
 * <p>{@code nullValuePropertyMappingStrategy = IGNORE} makes the update partial: an
 * omitted field is left alone, never nulled. Every other target property is
 * {@code ignore}d, so identity, tenancy, parentage, authorship and soft-delete
 * state are unreachable through this method.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TicketMessageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "ticketId", ignore = true)
    @Mapping(target = "authorUserId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    TicketMessage updateEntity(TicketMessageUpdateRequest request, @MappingTarget TicketMessage message);

    TicketMessageResponse toResponse(TicketMessage message);

    List<TicketMessageResponse> toResponseList(List<TicketMessage> messages);
}
