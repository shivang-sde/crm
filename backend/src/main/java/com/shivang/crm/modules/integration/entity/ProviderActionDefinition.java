package com.shivang.crm.modules.integration.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "provider_action_definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderActionDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderDefinition provider;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "action_name", nullable = false, length = 200)
    private String actionName;

    @Column(name = "endpoint_template", columnDefinition = "TEXT")
    private String endpointTemplate;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers_template", columnDefinition = "jsonb")
    private Map<String, Object> headersTemplate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_template", columnDefinition = "jsonb")
    private Map<String, Object> requestTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_template", columnDefinition = "jsonb")
    private Map<String, Object> responseTemplate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
