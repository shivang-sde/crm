package com.shivang.crm.modules.rbac.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "roles")
public class Role extends BaseEntity {

    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String level; // PLATFORM or TENANT
    
    @Column(name = "tenant_id")
    private UUID tenantId;
    
    @Column(name = "parent_role_id")
    private UUID parentRoleId;
    
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id", insertable = false, updatable = false)
    private Role parentRole;
    
    @OneToMany(mappedBy = "parentRole", fetch = FetchType.LAZY)
    private Set<Role> children = new HashSet<>();
}
