package com.shivang.crm.modules.auth;

import org.springframework.context.annotation.Configuration;

/**
 * Auth Module marker configuration.
 * 
 * This class serves as the module boundary marker for Spring Modulith.
 * All auth-related beans (controllers, services, repositories, security filters)
 * are organized under this package.
 * 
 * Module responsibilities:
 * - User authentication (login/logout)
 * - JWT access token generation & validation
 * - Refresh token rotation & storage
 * - Tenant context resolution from JWT claims
 * - Token blacklist (Redis) for logout invalidation
 * - Password management
 */
@Configuration
public class AuthModule {
    // Module marker — Spring Modulith uses this package as a module boundary
}
