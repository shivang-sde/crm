package com.shivang.crm.modules.tenant.entity;

import java.time.LocalDate;
import java.util.UUID;

import com.shivang.crm.modules.auth.entity.User;
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
@Table(name = "tenants")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "company_email")
    private String companyEmail;

    @Column(name = "company_phone")
    private String companyPhone;

    @Column(name = "website")
    private String website;

    @Column(name = "country")
    private String country;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "industry")
    private String industry;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "language")
    private String language;

    @Column(name = "plan_type", length = 50)
    @Builder.Default
    private String planType = "free";

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "reseller_id")
    private UUID resellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reseller_id", insertable = false, updatable = false)
    private User reseller;
}
