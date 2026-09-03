package com.shivang.crm.modules.form.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "form_fields",
    indexes = {
        @Index(name = "idx_form_fields_form_id", columnList = "form_id"),
        @Index(name = "idx_form_fields_form_order", columnList = "form_id, order_index")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_form_fields_form_key", columnNames = {"form_id", "field_key"})
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FormField extends BaseEntity {

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "field_key", length = 100, nullable = false)
    private String fieldKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private FormFieldType type;

    @Column(name = "label", length = 200, nullable = false)
    private String label;

    @Column(name = "placeholder", length = 200)
    private String placeholder;

    @Column(name = "help_text", columnDefinition = "TEXT")
    private String helpText;

    @Column(name = "required", nullable = false)
    @Default
    private Boolean required = false;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<Map<String, String>> options;

    @Column(name = "crm_target_type", length = 30)
    private String crmTargetType;

    @Column(name = "crm_target_field", length = 100)
    private String crmTargetField;

    @Column(name = "transform_type", length = 30, nullable = false)
    @Default
    private String transformType = "NONE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transform_config", columnDefinition = "jsonb")
    private Map<String, Object> transformConfig;
}
