package com.shivang.crm.shared.base;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class TenantOwnedEntity extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = true)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID createdBy;
}
