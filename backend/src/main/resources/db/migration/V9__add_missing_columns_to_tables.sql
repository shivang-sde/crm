CREATE UNIQUE INDEX IF NOT EXISTS uq_account_name
ON accounts(tenant_id, lower(name))
WHERE deleted = false;


CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_email
ON contacts(tenant_id, lower(email))
WHERE deleted = false
AND email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_phone
ON contacts(tenant_id, phone)
WHERE deleted = false
AND phone IS NOT NULL;

ALTER TABLE leads
ADD COLUMN converted_account_id UUID;

ALTER TABLE leads
ADD COLUMN converted_contact_id UUID;