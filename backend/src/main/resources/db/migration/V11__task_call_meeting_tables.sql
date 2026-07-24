-- ============================================================================
-- Task, Call, and Meeting Module - Database Schema
-- Version: 1.0
-- Description: Creates all tables for activity management (Tasks, Calls, Meetings)
--              with polymorphic linking to any entity type
-- ============================================================================

-- ============================================================================
-- 1. TASKS - Core task management with status, priority, due date
-- ============================================================================
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Core fields
    subject VARCHAR(255) NOT NULL,
    description TEXT,
    due_date TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    priority VARCHAR(20) DEFAULT 'MEDIUM',

    -- Polymorphic linking to any entity
    entity_type VARCHAR(50),
    entity_id UUID,

    -- Reminder
    remind_at TIMESTAMP,

    -- Recurrence (stored as JSONB)
    recurrence JSONB,

    -- Completion tracking
    completed_at TIMESTAMP,
    is_closed BOOLEAN DEFAULT FALSE,

    -- Custom data (JSONB for extensibility)
    custom_data JSONB,

    -- Ownership
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_tasks_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tasks_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_tasks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_tasks_tenant ON tasks(tenant_id);
CREATE INDEX idx_tasks_status ON tasks(tenant_id, status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_owner_user_id ON tasks(owner_user_id);
CREATE INDEX idx_tasks_entity ON tasks(entity_type, entity_id);
CREATE INDEX idx_tasks_is_closed ON tasks(tenant_id, is_closed);
CREATE INDEX idx_tasks_completed_at ON tasks(completed_at);
CREATE INDEX idx_tasks_custom_data ON tasks USING GIN(custom_data);
CREATE INDEX idx_tasks_recurrence ON tasks USING GIN(recurrence);

-- ============================================================================
-- 2. CALLS - Call logging with type, duration, status
-- ============================================================================
CREATE TABLE calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Core fields
    subject VARCHAR(255) NOT NULL,
    description TEXT,

    -- Call details
    call_type VARCHAR(20) NOT NULL, -- INCOMING, OUTGOING
    phone_number VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_minutes INTEGER,

    -- Polymorphic linking to any entity
    entity_type VARCHAR(50),
    entity_id UUID,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED', -- PLANNED, HELD, NOT_HELD, CANCELLED

    -- Reminder
    remind_at TIMESTAMP,

    -- Recurrence (stored as JSONB)
    recurrence JSONB,

    -- Custom data (JSONB for extensibility)
    custom_data JSONB,

    -- Ownership
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_calls_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_calls_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_calls_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_calls_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_calls_tenant ON calls(tenant_id);
CREATE INDEX idx_calls_status ON calls(tenant_id, status);
CREATE INDEX idx_calls_call_type ON calls(call_type);
CREATE INDEX idx_calls_start_time ON calls(start_time);
CREATE INDEX idx_calls_owner_user_id ON calls(owner_user_id);
CREATE INDEX idx_calls_entity ON calls(entity_type, entity_id);
CREATE INDEX idx_calls_custom_data ON calls USING GIN(custom_data);
CREATE INDEX idx_calls_recurrence ON calls USING GIN(recurrence);

-- ============================================================================
-- 3. MEETINGS - Meeting scheduling with location, attendees
-- ============================================================================
CREATE TABLE meetings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Core fields
    subject VARCHAR(255) NOT NULL,
    description TEXT,
    agenda TEXT,

    -- Location (address or video link)
    location TEXT,
    meeting_type VARCHAR(20), -- IN_PERSON, VIDEO, PHONE

    -- Timing
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,

    -- Attendees (JSON array of emails or contact IDs)
    attendees JSONB,

    -- Polymorphic linking to any entity
    entity_type VARCHAR(50),
    entity_id UUID,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED', -- PLANNED, HELD, NOT_HELD, CANCELLED

    -- Reminder
    remind_at TIMESTAMP,

    -- Recurrence (stored as JSONB)
    recurrence JSONB,

    -- Custom data (JSONB for extensibility)
    custom_data JSONB,

    -- Ownership
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,
    assigned_to UUID,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_meetings_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_meetings_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_meetings_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_meetings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT fk_meetings_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id)
);

CREATE INDEX idx_meetings_tenant ON meetings(tenant_id);
CREATE INDEX idx_meetings_status ON meetings(tenant_id, status);
CREATE INDEX idx_meetings_start_time ON meetings(start_time);
CREATE INDEX idx_meetings_owner_user_id ON meetings(owner_user_id);
CREATE INDEX idx_meetings_entity ON meetings(entity_type, entity_id);
CREATE INDEX idx_meetings_attendees ON meetings USING GIN(attendees);
CREATE INDEX idx_meetings_custom_data ON meetings USING GIN(custom_data);
CREATE INDEX idx_meetings_recurrence ON meetings USING GIN(recurrence);

-- ============================================================================
-- 4. REMINDER_JOBS - Scheduled reminders for background processing
-- ============================================================================
CREATE TABLE reminder_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Reference to the activity
    activity_type VARCHAR(20) NOT NULL, -- TASK, CALL, MEETING
    activity_id UUID NOT NULL,

    -- Reminder timing
    remind_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,

    -- Notification status
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, FAILED

    -- Recipient
    recipient_user_id UUID NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_reminder_jobs_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_reminder_jobs_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id)
);

CREATE INDEX idx_reminder_jobs_tenant ON reminder_jobs(tenant_id);
CREATE INDEX idx_reminder_jobs_activity ON reminder_jobs(activity_type, activity_id);
CREATE INDEX idx_reminder_jobs_remind_at ON reminder_jobs(remind_at);
CREATE INDEX idx_reminder_jobs_status ON reminder_jobs(status);

-- ============================================================================
-- 5. UNIFIED_ACTIVITIES - READ-ONLY view combining all activity types
-- ============================================================================
CREATE OR REPLACE VIEW unified_activities AS
SELECT
    t.id,
    t.tenant_id,
    'TASK' AS activity_type,
    t.subject,
    t.description,
    t.due_date AS start_time,
    t.due_date AS end_time,
    t.status,
    t.priority,
    NULL::VARCHAR(20) AS call_type,
    NULL::VARCHAR(50) AS phone_number,
    NULL::INTEGER AS duration_minutes,
    NULL::TEXT AS agenda,
    NULL::TEXT AS location,
    NULL::VARCHAR(20) AS meeting_type,
    NULL::JSONB AS attendees,
    t.entity_type,
    t.entity_id,
    t.remind_at,
    t.recurrence,
    t.is_closed,
    t.completed_at,
    t.owner_user_id,
    t.created_by,
    t.updated_by,
    t.created_at,
    t.updated_at,
    t.deleted
FROM tasks t
WHERE t.deleted = FALSE

UNION ALL

SELECT
    c.id,
    c.tenant_id,
    'CALL' AS activity_type,
    c.subject,
    c.description,
    c.start_time,
    c.end_time,
    c.status,
    NULL::VARCHAR(20) AS priority,
    c.call_type,
    c.phone_number,
    c.duration_minutes,
    NULL::TEXT AS agenda,
    NULL::TEXT AS location,
    NULL::VARCHAR(20) AS meeting_type,
    NULL::JSONB AS attendees,
    c.entity_type,
    c.entity_id,
    c.remind_at,
    c.recurrence,
    NULL::BOOLEAN AS is_closed,
    NULL::TIMESTAMP AS completed_at,
    c.owner_user_id,
    c.created_by,
    c.updated_by,
    c.created_at,
    c.updated_at,
    c.deleted
FROM calls c
WHERE c.deleted = FALSE

UNION ALL

SELECT
    m.id,
    m.tenant_id,
    'MEETING' AS activity_type,
    m.subject,
    m.description,
    m.start_time,
    m.end_time,
    m.status,
    NULL::VARCHAR(20) AS priority,
    NULL::VARCHAR(20) AS call_type,
    NULL::VARCHAR(50) AS phone_number,
    NULL::INTEGER AS duration_minutes,
    m.agenda,
    m.location,
    m.meeting_type,
    m.attendees,
    m.entity_type,
    m.entity_id,
    m.remind_at,
    m.recurrence,
    NULL::BOOLEAN AS is_closed,
    NULL::TIMESTAMP AS completed_at,
    m.owner_user_id,
    m.created_by,
    m.updated_by,
    m.created_at,
    m.updated_at,
    m.deleted
FROM meetings m
WHERE m.deleted = FALSE;
