package com.shivang.crm.modules.rbac.entity;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "permissions")
public class Permission extends BaseEntity {
    
    @Column(nullable = false)
    private String module;
    
    @Column(nullable = false)
    private String action;
  
    private String description;
}
