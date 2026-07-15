package com.shivang.crm.modules.auth.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "tenant_id", "email" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "manager_id")
    private UUID managerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", insertable = false, updatable = false)
    private User manager;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @OneToMany(mappedBy = "reseller", fetch = FetchType.LAZY)
    private Set<Tenant> managedTenants = new HashSet<>();

    /*resolved username  */
    public String getDisplayName() {
    if (firstName != null && !firstName.isBlank() &&
        lastName != null && !lastName.isBlank()) {
        return firstName + " " + lastName;
    }
    if (firstName != null && !firstName.isBlank()) {
        return firstName;
    }
    if (lastName != null && !lastName.isBlank()) {
        return lastName;
    }
    return email; // fallback
}


}
