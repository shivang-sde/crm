package com.shivang.crm.modules.rbac.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "role_permissions")
public class RolePermission extends BaseEntity {
    
    
    @Column(name = "role_id", nullable = false)
    private UUID roleId;
    
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;
    
    @Column(name = "access_scope", nullable = false)
    private String accessScope; // ALL, TEAM, OWN, NONE
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private Permission permission;
}
