package com.shivang.crm.modules.lead.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "lead_custom_fields",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadCustomField extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(length = 100, nullable = false)
    private String fieldKey;

    @Column(length = 200, nullable = false)
    private String fieldLabel;

    @Column(length = 50, nullable = false)
    private String fieldType; // TEXT, TEXTAREA, NUMBER, EMAIL, PHONE, DATE, BOOLEAN, SELECT, MULTISELECT, URL

    @Column(columnDefinition = "boolean default false")
    private Boolean isRequired = false;

    @Column(columnDefinition = "boolean default true")
    private Boolean isActive = true;

    @Column(name = "display_order", columnDefinition = "integer default 0")
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json")
    private List<Map<String, String>> optionsJson; // For SELECT/MULTISELECT: [{"label":"Option 1","value":"opt_1"},...]
}
