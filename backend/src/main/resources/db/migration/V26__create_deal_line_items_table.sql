-- ============================================================================
-- Deal line items
-- ============================================================================

CREATE TABLE deal_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    item_name VARCHAR(255),
    item_code VARCHAR(100),
    description TEXT,
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,2),
    discount_amount NUMERIC(19,2),
    tax_amount NUMERIC(19,2),
    line_total NUMERIC(19,2),
    service_start_date DATE,
    service_end_date DATE,
    renewable BOOLEAN,
    renewal_notice_days INTEGER,
    custom_data JSONB,
    updated_by UUID,

    CONSTRAINT fk_deal_line_items_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_deal_line_items_deal_id FOREIGN KEY (deal_id) REFERENCES deals(id),
    CONSTRAINT fk_deal_line_items_offering_id FOREIGN KEY (offering_id) REFERENCES offerings(id)
);

CREATE INDEX idx_deal_line_items_tenant ON deal_line_items(tenant_id);
CREATE INDEX idx_deal_line_items_deal ON deal_line_items(tenant_id, deal_id);
CREATE INDEX idx_deal_line_items_offering ON deal_line_items(tenant_id, offering_id);
CREATE INDEX idx_deal_line_items_service_end ON deal_line_items(tenant_id, service_end_date);
