

ALTER TABLE leads
ADD COLUMN IF NOT EXISTS last_contacted_at TIMESTAMP DEFAULT NOW();

ALTER TABLE entity_history
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;


ALTER TABLE users
ADD COLUMN manager_id UUID NULL;

ALTER TABLE users
ADD CONSTRAINT fk_users_manager
FOREIGN KEY (manager_id)
REFERENCES users(id);

CREATE INDEX idx_users_manager_id
ON users(manager_id);