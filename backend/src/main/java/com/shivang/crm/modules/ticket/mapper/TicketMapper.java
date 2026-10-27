package com.shivang.crm.modules.ticket.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;

/**
 * L3.0-P5 contract: the DTOs and the entity share camelCase field names, so this
 * mapper needs no renaming annotations. That is deliberate — a {@code @Mapping}
 * renaming here would be a signal the wire contract had drifted.
 *
 * <p>{@code nullValuePropertyMappingStrategy = IGNORE} makes this a partial update:
 * an omitted field is left alone, never nulled. TicketUpdateRequest exposes only the
 * five mutable fields, so identity, lifecycle and soft-delete state cannot be
 * reached through it.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TicketMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    // L3.1-P4: SLA state is server-owned. Deadlines are stamped once at creation and
    // firstResponseAt/slaBreached move only through the message path and the breach
    // scanner, so no PATCH may reach them.
    @Mapping(target = "responseDueAt", ignore = true)
    @Mapping(target = "resolutionDueAt", ignore = true)
    @Mapping(target = "firstResponseAt", ignore = true)
    @Mapping(target = "slaBreached", ignore = true)
    Ticket updateEntity(TicketUpdateRequest request, @MappingTarget Ticket ticket);

    TicketResponse toResponse(Ticket ticket);

    List<TicketResponse> toResponseList(List<Ticket> tickets);
}
