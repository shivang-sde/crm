package com.shivang.crm.modules.integration.controller;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.integration.entity.ConnectorUserAgent;
import com.shivang.crm.modules.integration.service.impl.ConnectorUserAgentService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/connector-user-agents")
@RequiredArgsConstructor
public class ConnectorUserAgentController {

    private final ConnectorUserAgentService service;
    private final TenantContext tenantContext;

    public record CreateRequest(

            @NotNull(message = "connectorInstanceId is required")
            UUID connectorInstanceId,

            @NotNull(message = "userId is required")
            UUID userId,

            @Size(
                max = 150,
                message = "externalAgentId cannot exceed 150 characters"
            )
            String externalAgentId,

            @Size(
                max = 100,
                message = "externalAgentNumber cannot exceed 100 characters"
            )
            String externalAgentNumber,

            Boolean active
    ) {
    }

    public record UpdateRequest(

            @NotNull(message = "userId is required")
            UUID userId,

            @Size(
                max = 150,
                message = "externalAgentId cannot exceed 150 characters"
            )
            String externalAgentId,

            @Size(
                max = 100,
                message = "externalAgentNumber cannot exceed 100 characters"
            )
            String externalAgentNumber,

            Boolean active
    ) {
    }

    public record StatusRequest(
            @NotNull(message = "active is required")
            Boolean active
    ) {
    }

    public record ConnectorUserAgentResponse(
            UUID id,
            UUID tenantId,
            UUID connectorInstanceId,
            String connectorName,
            String providerKey,
            UUID userId,
            String userName,
            String userEmail,
            String externalAgentId,
            String externalAgentNumber,
            Boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<ConnectorUserAgentResponse>>> findAll(

            @RequestParam(required = false)
            UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();

        List<ConnectorUserAgentResponse> response =
                service.findAll(
                        tenantId,
                        connectorInstanceId
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<
            ApiResponse<ConnectorUserAgentResponse>> findById(

            @PathVariable UUID id) {

        ConnectorUserAgent mapping =
                service.findById(
                        requireTenantId(),
                        id
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        toResponse(mapping)
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<
            ApiResponse<ConnectorUserAgentResponse>> findMyMapping(

            @RequestParam UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        ConnectorUserAgent mapping =
                service.findForUser(
                        tenantId,
                        connectorInstanceId,
                        userId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "NOT_FOUND",
                                "No active provider-agent mapping is configured for this user"
                        )
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        toResponse(mapping)
                )
        );
    }

    @PostMapping
    public ResponseEntity<
            ApiResponse<ConnectorUserAgentResponse>> create(

            @Valid
            @RequestBody CreateRequest request) {

        ConnectorUserAgent created =
                service.create(
                        requireTenantId(),
                        requireUserId(),
                        request.connectorInstanceId(),
                        request.userId(),
                        request.externalAgentId(),
                        request.externalAgentNumber(),
                        request.active()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponse.success(
                            toResponse(created)
                    )
                );
    }

    @PutMapping("/{id}")
    public ResponseEntity<
            ApiResponse<ConnectorUserAgentResponse>> update(

            @PathVariable UUID id,

            @Valid
            @RequestBody UpdateRequest request) {

        ConnectorUserAgent updated =
                service.update(
                        requireTenantId(),
                        requireUserId(),
                        id,
                        request.userId(),
                        request.externalAgentId(),
                        request.externalAgentNumber(),
                        request.active()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        toResponse(updated)
                )
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<
            ApiResponse<ConnectorUserAgentResponse>> updateStatus(

            @PathVariable UUID id,

            @Valid
            @RequestBody StatusRequest request) {

        ConnectorUserAgent updated =
                service.updateStatus(
                        requireTenantId(),
                        requireUserId(),
                        id,
                        request.active()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        toResponse(updated)
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id) {

        service.delete(
                requireTenantId(),
                requireUserId(),
                id
        );

        return ResponseEntity.ok(
                ApiResponse.success(null)
        );
    }

    private ConnectorUserAgentResponse toResponse(
            ConnectorUserAgent mapping) {

        String connectorName = null;
        String providerKey = null;

        if (mapping.getConnectorInstance() != null) {
            connectorName =
                    mapping.getConnectorInstance()
                            .getConnectorName();

            if (mapping.getConnectorInstance()
                    .getProvider() != null) {

                providerKey =
                        mapping.getConnectorInstance()
                                .getProvider()
                                .getProviderKey();
            }
        }

        String userName = null;
        String userEmail = null;

        if (mapping.getUser() != null) {
            userName = mapping.getUser().getDisplayName();
            userEmail = mapping.getUser().getEmail();
        }

        return new ConnectorUserAgentResponse(
                mapping.getId(),
                mapping.getTenantId(),
                mapping.getConnectorInstance().getId(),
                connectorName,
                providerKey,
                mapping.getUserId(),
                userName,
                userEmail,
                mapping.getExternalAgentId(),
                mapping.getExternalAgentNumber(),
                mapping.getIsActive(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt()
        );
    }

    private UUID requireTenantId() {
        if (!tenantContext.hasTenant()) {
            throw new IllegalStateException(
                    "Tenant context is not available"
            );
        }

        return tenantContext.getTenantId();
    }

    private UUID requireUserId() {
        if (!tenantContext.hasUser()) {
            throw new IllegalStateException(
                    "User context is not available"
            );
        }

        return tenantContext.getUserId();
    }
}