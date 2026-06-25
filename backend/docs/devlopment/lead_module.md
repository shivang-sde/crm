# Lead Management Module - Complete Architecture & Implementation Guide

> **Date**: June 2026  
> **Module**: Lead Management (Lead Module)  
> **Version**: 1.0  
> **Status**: Architecture & Implementation Guide

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Database Design](#database-design)
3. [Entity Design](#entity-design)
4. [API Design](#api-design)
5. [Frontend Architecture](#frontend-architecture)
6. [Implementation Phases](#implementation-phases)
7. [Extensibility for Integrations](#extensibility-for-integrations)

---

## Architecture Overview

### Core Principles

- **Multi-Tenant**: All queries filtered by `tenant_id`
- **RBAC Integration**: Existing permission system (`lead.read`, `lead.write`, etc.)
- **Dynamic Fields**: Custom fields stored in JSONB (NOT EAV pattern)
- **Event-Driven**: Lead changes publish events for workflow automation
- **Audit Trail**: All changes tracked in `lead_activities`
- **Modular**: Spring Modulith structure, loosely coupled

### Module Structure

```
modules/lead/
├── LeadModule.java              (Spring Modulith)
├── config/
│   └── LeadConfig.java
├── controller/
│   ├── LeadController.java
│   ├── LeadActivityController.java
│   └── LeadNoteController.java
├── dto/
│   ├── LeadCreateRequest.java
│   ├── LeadUpdateRequest.java
│   ├── LeadResponse.java
│   ├── LeadDetailResponse.java
│   ├── LeadActivityResponse.java
│   └── LeadCustomFieldResponse.java
├── entity/
│   ├── Lead.java
│   ├── LeadStatus.java
│   ├── LeadSource.java
│   ├── LeadCustomField.java
│   ├── LeadActivity.java
│   └── LeadNote.java
├── event/
│   ├── LeadCreatedEvent.java
│   ├── LeadUpdatedEvent.java
│   ├── LeadConvertedEvent.java
│   └── LeadActivityPublisher.java
├── mapper/
│   ├── LeadMapper.java
│   ├── LeadActivityMapper.java
│   └── LeadNoteMapper.java
├── repository/
│   ├── LeadRepository.java
│   ├── LeadStatusRepository.java
│   ├── LeadSourceRepository.java
│   ├── LeadCustomFieldRepository.java
│   ├── LeadActivityRepository.java
│   ├── LeadNoteRepository.java
│   └── LeadSpecifications.java
├── service/
│   ├── LeadService.java
│   ├── LeadActivityService.java
│   ├── LeadNoteService.java
│   └── LeadSearchService.java
└── validation/
    └── LeadValidator.java
```

---

## Database Design

### 1. Lead Status Master

**Table**: `lead_statuses`

Tenant-specific lead statuses. Examples:
- Tenant A: New → Contacted → Qualified → Won/Lost
- Tenant B: Fresh → Follow Up → Demo → Converted

```sql
CREATE TABLE lead_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    display_order INT,
    is_default BOOLEAN DEFAULT FALSE,
    is_closed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_lead_statuses_tenant_id ON lead_statuses(tenant_id);
```

---

### 2. Lead Source Master

**Table**: `lead_sources`

Track where leads originate.

```sql
CREATE TABLE lead_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_lead_sources_tenant_id ON lead_sources(tenant_id);
```

---

### 3. Lead Custom Fields Definition

**Table**: `lead_custom_fields`

Defines custom fields for leads. Tenant-specific.

```sql
CREATE TABLE lead_custom_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(50) NOT NULL, -- TEXT, NUMBER, DATE, SELECT, MULTISELECT, etc.
    is_required BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    options_json JSONB, -- For SELECT/MULTISELECT: [{"label":"...", "value":"..."}]
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    UNIQUE(tenant_id, field_key)
);

CREATE INDEX idx_lead_custom_fields_tenant_id ON lead_custom_fields(tenant_id);
CREATE INDEX idx_lead_custom_fields_active ON lead_custom_fields(tenant_id, is_active);
```

---

### 4. Main Lead Table

**Table**: `leads`

Core lead data with standard CRM fields + JSONB for custom fields.

```sql
CREATE TABLE leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    -- Standard CRM fields
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(20),
    company VARCHAR(200),
    
    -- Status & Source
    status_id UUID NOT NULL,
    source_id UUID,
    
    -- Ownership
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,
    
    -- Lead tracking
    score INT DEFAULT 0,
    notes TEXT,
    
    -- Conversion
    is_converted BOOLEAN DEFAULT FALSE,
    converted_at TIMESTAMP,
    
    -- Custom fields (JSONB - NOT EAV)
    custom_data JSONB DEFAULT '{}',
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    FOREIGN KEY (status_id) REFERENCES lead_statuses(id),
    FOREIGN KEY (source_id) REFERENCES lead_sources(id),
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Indexes for common queries
CREATE INDEX idx_leads_tenant_id ON leads(tenant_id);
CREATE INDEX idx_leads_status_id ON leads(status_id);
CREATE INDEX idx_leads_owner_user_id ON leads(owner_user_id);
CREATE INDEX idx_leads_email ON leads(tenant_id, email);
CREATE INDEX idx_leads_phone ON leads(tenant_id, phone);
CREATE INDEX idx_leads_created_at ON leads(tenant_id, created_at DESC);
CREATE INDEX idx_leads_is_converted ON leads(tenant_id, is_converted);
CREATE INDEX idx_leads_search ON leads USING GIN (custom_data);
```

---

### 5. Lead Activity Timeline

**Table**: `lead_activities`

Immutable audit trail of all lead actions.

```sql
CREATE TABLE lead_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    lead_id UUID NOT NULL,
    
    activity_type VARCHAR(50) NOT NULL, -- LEAD_CREATED, LEAD_UPDATED, STATUS_CHANGED, etc.
    description TEXT,
    performed_by UUID NOT NULL,
    
    -- Store old/new values for tracking changes
    metadata JSONB DEFAULT '{}',
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    FOREIGN KEY (performed_by) REFERENCES users(id)
);

-- Indexes
CREATE INDEX idx_lead_activities_lead_id ON lead_activities(lead_id);
CREATE INDEX idx_lead_activities_tenant_id ON lead_activities(tenant_id);
CREATE INDEX idx_lead_activities_created_at ON lead_activities(created_at DESC);
CREATE INDEX idx_lead_activities_activity_type ON lead_activities(activity_type);
```

Activity types:
- `LEAD_CREATED`
- `LEAD_UPDATED`
- `STATUS_CHANGED`
- `OWNER_CHANGED`
- `NOTE_ADDED`
- `CALL_LOGGED`
- `EMAIL_SENT`
- `WHATSAPP_SENT`
- `CONVERTED`

---

### 6. Lead Notes

**Table**: `lead_notes`

Simple note management for leads.

```sql
CREATE TABLE lead_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    lead_id UUID NOT NULL,
    
    note TEXT NOT NULL,
    created_by UUID NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_lead_notes_lead_id ON lead_notes(lead_id);
CREATE INDEX idx_lead_notes_tenant_id ON lead_notes(tenant_id);
CREATE INDEX idx_lead_notes_created_at ON lead_notes(created_at DESC);
```

---

## Entity Design

### Lead Entity

```java
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Lead extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    // Standard CRM fields
    @Column(length = 100, nullable = false)
    private String firstName;
    
    @Column(length = 100)
    private String lastName;
    
    @Column(length = 255)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(length = 200)
    private String company;
    
    // Status & Source
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private LeadStatus status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private LeadSource source;
    
    // Ownership
    @Column(name = "owner_user_id")
    private UUID ownerUserId;
    
    @Column(nullable = false)
    private UUID createdBy;
    
    @Column(name = "updated_by")
    private UUID updatedBy;
    
    // Lead tracking
    @Column(columnDefinition = "integer default 0")
    private Integer score;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    // Conversion
    @Column(columnDefinition = "boolean default false")
    private Boolean isConverted;
    
    @Column(name = "converted_at")
    private Instant convertedAt;
    
    // CRITICAL: JSONB for custom fields (NOT EAV)
    @Column(columnDefinition = "jsonb default '{}'")
    private String customData; // Stored as JSON string, parsed as Map<String, Object>
    
    // Helper methods
    public Map<String, Object> getCustomDataMap() {
        if (customData == null || customData.equals("{}")) {
            return new HashMap<>();
        }
        // Parse JSON to Map (use ObjectMapper)
        return new HashMap<>();
    }
}
```

### LeadStatus Entity

```java
@Entity
@Table(name = "lead_statuses", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadStatus extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    @Column(length = 100, nullable = false)
    private String name;
    
    @Column(length = 20)
    private String color;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(columnDefinition = "boolean default false")
    private Boolean isDefault;
    
    @Column(columnDefinition = "boolean default false")
    private Boolean isClosed;
}
```

### LeadSource Entity

```java
@Entity
@Table(name = "lead_sources",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadSource extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    @Column(length = 100, nullable = false)
    private String name;
    
    @Column(columnDefinition = "boolean default true")
    private Boolean isActive;
}
```

### LeadCustomField Entity

```java
@Entity
@Table(name = "lead_custom_fields",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadCustomField extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    @Column(length = 100, nullable = false)
    private String fieldKey;
    
    @Column(length = 200, nullable = false)
    private String fieldLabel;
    
    @Column(length = 50, nullable = false)
    private String fieldType; // TEXT, NUMBER, DATE, SELECT, etc.
    
    @Column(columnDefinition = "boolean default false")
    private Boolean isRequired;
    
    @Column(columnDefinition = "boolean default true")
    private Boolean isActive;
    
    @Column(name = "display_order", columnDefinition = "integer default 0")
    private Integer displayOrder;
    
    @Column(columnDefinition = "jsonb")
    private String optionsJson; // For SELECT/MULTISELECT
}
```

### LeadActivity Entity

```java
@Entity
@Table(name = "lead_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadActivity extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;
    
    @Column(length = 50, nullable = false)
    private String activityType;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private UUID performedBy;
    
    @Column(columnDefinition = "jsonb default '{}'")
    private String metadata;
}
```

### LeadNote Entity

```java
@Entity
@Table(name = "lead_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LeadNote extends BaseEntity {
    
    @Column(nullable = false)
    private UUID tenantId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String note;
    
    @Column(nullable = false)
    private UUID createdBy;
    
    @Column(nullable = false)
    private UUID updatedBy;
}
```

---

## API Design

### Lead CRUD APIs

```http
# List leads with filtering, searching, pagination
GET /api/v1/leads?page=0&size=20&sort=createdAt,desc&search=john&status=NEW&owner=<uuid>

# Create lead
POST /api/v1/leads
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+91-9876543210",
  "company": "ABC Corp",
  "statusId": "uuid",
  "sourceId": "uuid",
  "ownerUserId": "uuid",
  "score": 50,
  "customData": {
    "vehicle_type": "SUV",
    "budget": "1500000"
  }
}

# Get lead details
GET /api/v1/leads/{id}

# Update lead
PUT /api/v1/leads/{id}
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  ...
}

# Delete lead
DELETE /api/v1/leads/{id}

# Assign lead
PUT /api/v1/leads/{id}/assign
Content-Type: application/json

{
  "ownerUserId": "uuid"
}

# Change status
PUT /api/v1/leads/{id}/status
Content-Type: application/json

{
  "statusId": "uuid"
}

# Convert lead
POST /api/v1/leads/{id}/convert
Content-Type: application/json

{
  "accountName": "ABC Corp",
  "dealName": "Initial Deal",
  "dealValue": 100000
}
```

### Activities & Notes APIs

```http
# Get lead activities
GET /api/v1/leads/{id}/activities?page=0&size=50

# Get lead notes
GET /api/v1/leads/{id}/notes?page=0&size=50

# Add note
POST /api/v1/leads/{id}/notes
Content-Type: application/json

{
  "note": "Customer interested in demo"
}

# Update note
PUT /api/v1/leads/{id}/notes/{noteId}
Content-Type: application/json

{
  "note": "Updated note"
}

# Delete note
DELETE /api/v1/leads/{id}/notes/{noteId}
```

### Masters APIs

```http
# Lead Statuses
GET /api/v1/leads/statuses
POST /api/v1/leads/statuses
PUT /api/v1/leads/statuses/{id}
DELETE /api/v1/leads/statuses/{id}

# Lead Sources
GET /api/v1/leads/sources
POST /api/v1/leads/sources
PUT /api/v1/leads/sources/{id}
DELETE /api/v1/leads/sources/{id}

# Custom Fields
GET /api/v1/leads/custom-fields
POST /api/v1/leads/custom-fields
PUT /api/v1/leads/custom-fields/{id}
DELETE /api/v1/leads/custom-fields/{id}
```

### Standard Response Format

```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "firstName": "John",
    ...
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 150
  }
}
```

Error response:

```json
{
  "success": false,
  "error": {
    "code": "ACCESS_DENIED",
    "message": "You do not have permission"
  }
}
```

---

## Frontend Architecture

### Component Structure

```
frontend/src/components/leads/
├── LeadList/
│   ├── LeadList.tsx
│   ├── LeadTable.tsx
│   ├── LeadFilters.tsx
│   ├── LeadSearch.tsx
│   └── LeadPagination.tsx
├── LeadKanban/
│   ├── LeadKanban.tsx
│   ├── LeadColumn.tsx
│   └── LeadCard.tsx
├── LeadDetail/
│   ├── LeadDetail.tsx
│   ├── LeadBasicInfo.tsx
│   ├── LeadCustomFields.tsx
│   ├── LeadTimeline.tsx
│   ├── LeadNotes.tsx
│   └── LeadAssignment.tsx
├── LeadForm/
│   ├── LeadForm.tsx
│   ├── DynamicFieldRenderer.tsx
│   └── CustomFieldInputs.tsx
└── shared/
    ├── StatusBadge.tsx
    └── SourceBadge.tsx
```

### Pages

```
frontend/src/app/(tenant)/
├── leads/
│   ├── page.tsx          (List view with tabs)
│   ├── [id]/
│   │   └── page.tsx      (Lead detail)
│   ├── new/
│   │   └── page.tsx      (Create lead form)
│   └── kanban/
│       └── page.tsx      (Kanban board)
```

### TanStack Query Hooks

```typescript
// hooks/leads/useLeads.ts
export const useLeads = (filters: LeadFilters, page: number, size: number) => {
  return useQuery({
    queryKey: ['leads', filters, page, size],
    queryFn: () => leadApi.getLeads(filters, page, size),
  });
};

// hooks/leads/useLead.ts
export const useLead = (id: string) => {
  return useQuery({
    queryKey: ['lead', id],
    queryFn: () => leadApi.getLead(id),
  });
};

// hooks/leads/useCreateLead.ts
export const useCreateLead = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadCreateRequest) => leadApi.createLead(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leads'] });
    },
  });
};
```

### DynamicFieldRenderer Component

The frontend must render form fields dynamically based on backend field definitions:

```typescript
interface DynamicFieldRendererProps {
  fields: LeadCustomFieldResponse[];
  values: Record<string, any>;
  errors: Record<string, string>;
  onChange: (fieldKey: string, value: any) => void;
}

export const DynamicFieldRenderer: React.FC<DynamicFieldRendererProps> = ({
  fields,
  values,
  errors,
  onChange,
}) => {
  return (
    <div>
      {fields
        .filter(f => f.isActive)
        .sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0))
        .map(field => (
          <div key={field.id}>
            {field.fieldType === 'TEXT' && (
              <Input
                value={values[field.fieldKey] || ''}
                onChange={e => onChange(field.fieldKey, e.target.value)}
              />
            )}
            {field.fieldType === 'SELECT' && (
              <Select
                options={field.options}
                value={values[field.fieldKey]}
                onChange={v => onChange(field.fieldKey, v)}
              />
            )}
            {/* ... other field types ... */}
          </div>
        ))}
    </div>
  );
};
```

---

## Implementation Phases

### Phase 1: Core Lead Management (Sprint 4)

**Deliverables**:
- ✅ Lead, LeadStatus, LeadSource entities
- ✅ Database migrations (Flyway)
- ✅ Repository layer with Specifications
- ✅ Service layer
- ✅ CRUD controllers
- ✅ Search/Filter/Pagination
- ✅ Assignment functionality
- ✅ Activity logging
- ✅ Frontend: Lead list, filters, pagination

**Estimated effort**: 2-3 weeks

### Phase 2: Dynamic Fields & Timeline (Sprint 5)

**Deliverables**:
- ✅ LeadCustomField & LeadCustomFieldValue entities
- ✅ LeadActivity & LeadNote entities
- ✅ Activity timeline API
- ✅ Notes management
- ✅ Custom field APIs
- ✅ Frontend: Lead detail page, timeline, notes, Kanban board

**Estimated effort**: 2-3 weeks

### Phase 3: Lead Conversion & Advanced Features (Sprint 6)

**Deliverables**:
- ✅ Lead conversion logic (Account → Contact → Deal)
- ✅ Account module
- ✅ Contact module
- ✅ Deal module
- ✅ Conversion APIs
- ✅ Lead conversion workflow

**Estimated effort**: 3-4 weeks

---

## Extensibility for Integrations

### Design for Future Integrations

All lead creation/updates must support incoming data from multiple sources:

1. **Manual CRM Entry** (UI forms)
2. **WhatsApp Integration**
3. **Cloud Telephony**
4. **Website Lead Capture**
5. **Meta/Facebook Lead Forms**
6. **Google Ads Leads**
7. **Email Integrations**

### Key Requirements

1. **Field Mapping**: Map incoming fields to custom fields
2. **Webhook Support**: Accept POST requests from integrations
3. **Duplicate Detection**: Avoid duplicate leads
4. **Activity Tracking**: Log source of lead creation
5. **Queue-Based Processing**: Use async processing for integrations

### Integration API (Future)

```http
# Webhook for external integrations
POST /api/v1/integration/leads
Content-Type: application/json

{
  "apiKey": "integration-key",
  "source": "whatsapp|google_ads|facebook|etc",
  "tenantId": "uuid",
  "data": {
    "firstName": "John",
    "email": "john@example.com",
    "phone": "+91-9876543210",
    "customFields": {
      "vehicle_type": "SUV"
    }
  }
}
```

---

## Security & RBAC

### Required Permissions

```
lead.read       - View leads
lead.write      - Create/Update leads
lead.delete     - Delete leads
lead.assign     - Assign leads to users
lead.export     - Export lead data
```

### Access Control

- **Read**: User can see leads where: owner OR in team OR has ALL access
- **Write**: Only owner or ADMIN
- **Assign**: Only ADMIN or MANAGER
- **Delete**: Only ADMIN

### Query Example

```java
// In service layer
List<Lead> leads = leadRepository.findAll(
    Specification
        .where(LeadSpecifications.byTenantId(tenantId))
        .and(LeadSpecifications.byAccessible(currentUserId, userTeams, hasAllAccess))
        .and(LeadSpecifications.byStatus(statusId))
        .and(LeadSpecifications.bySearch(searchTerm))
);
```

---

## Key Design Decisions & Why

| Decision | Why |
|----------|-----|
| **JSONB for custom fields** | Performance at scale, no complex EAV joins, easy to extend |
| **Immutable activities table** | Audit trail, compliance, easy to query history |
| **Separate notes table** | Performance, separate lifecycle from lead |
| **Spring Modulith** | Modular architecture, loose coupling, future microservices |
| **Specifications pattern** | Type-safe queries, reusable, testable |
| **Event publishing** | Decoupled workflows, extensibility, async processing |
| **Tenant filtering in all queries** | Security, multi-tenancy guarantee |

---

## What's NOT Included (Future Modules)

- ✗ Lead conversion (Account/Contact/Deal creation)
- ✗ WhatsApp/Telephony integrations
- ✗ Workflow automation
- ✗ Email campaigns
- ✗ Lead scoring algorithms
- ✗ Duplicate detection
- ✗ Field validation rules engine
- ✗ Bulk operations

These will be added in subsequent phases and modules.

---

## Testing Strategy

### Unit Tests
- Repository Specifications
- Service layer business logic
- MapStruct mappers

### Integration Tests
- End-to-end API flows
- Database transactions
- Tenant isolation

### E2E Tests (Frontend)
- Lead creation flow
- Search/Filter functionality
- Kanban drag-and-drop
- Form validation

---

## Performance Considerations

1. **Pagination**: Always paginate (default 20 items)
2. **N+1 Queries**: Use eager loading for status/source
3. **JSONB Queries**: Index custom_data column
4. **Search**: Full-text search on email/phone/name (future: PostgreSQL FTS)
5. **Caching**: Cache lead statuses/sources per tenant

---

## Monitoring & Observability

Metrics to track:
- Lead creation rate per tenant
- Search performance
- API response times
- Error rates by endpoint
- Activity processing latency

---

This architecture supports all requirements and is extensible for future integrations without database redesign.
