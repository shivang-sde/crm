CREATE TABLE user_notifications (
  id UUID NOT NULL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  reminder_id UUID,
  notification_type VARCHAR(20) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message CLOB,
  reference_type VARCHAR(50),
  reference_id UUID,
  is_read BOOLEAN NOT NULL,
  read_at TIMESTAMP,
  metadata CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted BOOLEAN NOT NULL,
  deleted_at TIMESTAMP,
  deleted_by UUID
);

CREATE INDEX idx_user_notifications_tenant ON user_notifications(tenant_id);
CREATE INDEX idx_user_notifications_tenant_user_read_created ON user_notifications(tenant_id, user_id, is_read, created_at);
CREATE INDEX idx_user_notifications_reference ON user_notifications(reference_type, reference_id);
