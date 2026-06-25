-- =============================================================
-- V1__create_auth_tables.sql
-- Auth module: tenants, users, refresh_tokens
-- =============================================================

-- Tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    company_email VARCHAR(255),
    company_phone VARCHAR(50),
    website VARCHAR(255),
    country VARCHAR(100),
    state VARCHAR(100),
    city VARCHAR(100),
    address_line1 VARCHAR(255),
    postal_code VARCHAR(20),
    logo_url VARCHAR(500),
    primary_color VARCHAR(50),
    industry VARCHAR(100),
    timezone VARCHAR(100),
    currency_code VARCHAR(3),
    language VARCHAR(10),
    plan_type VARCHAR(50) DEFAULT 'free',
    max_users INTEGER,
    subscription_end_date TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    reseller_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role_id UUID,
    is_active BOOLEAN DEFAULT true,
    email_verified BOOLEAN DEFAULT false,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- FK constraint for tenants.reseller_id
ALTER TABLE tenants
ADD CONSTRAINT fk_tenants_reseller
FOREIGN KEY (reseller_id)
REFERENCES users(id);

-- Tenant users unique per tenant
CREATE UNIQUE INDEX uq_users_tenant_email
ON users(tenant_id, email)
WHERE tenant_id IS NOT NULL;

-- Platform users unique globally
CREATE UNIQUE INDEX uq_users_platform_email
ON users(email)
WHERE tenant_id IS NULL;

-- Indexes
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);