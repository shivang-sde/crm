# Deal Module - Implementation Guide

## Architecture & Design Decisions

### 1. **Inheritance Hierarchy**

```
BaseEntity (abstract)
├── DealStage
└── TenantOwnedEntity
    └── Deal
```

**Why this design?**
- **BaseEntity**: Provides common fields (id, created_at, updated_at, soft delete)
- **TenantOwnedEntity**: Adds multi-tenancy (tenantId), ownership (ownerId), and creator tracking
- **DealStage**: Simple master data, doesn't need ownership
- **Deal**: Full-fledged business entity with multi-tenancy and RBAC

**Benefits:**
- Reusable base functionality
- Clear separation of concerns
- Consistent audit trails across all entities

---

### 2. **Repository Pattern with Specifications**

Instead of creating many custom query methods, we use JPA Specifications for flexible, composable queries:

```java
// Bad approach (many methods):
public List<Deal> findByTenantIdAndStageIdAndOwnerUserIdAndIsWon(...)

// Good approach (composable):
Specification<Deal> spec = Specifications.byTenantId(tenantId)
    .and(Specifications.byStageId(stageId))
    .and(Specifications.byOwnerUserId(ownerUserId))
    .and(Specifications.byIsWon(true));
Page<Deal> deals = dealRepository.findAll(spec, pageable);
```

**Benefits:**
- Reusable filter logic
- Type-safe query building
- Easy to add/remove filters without new methods
- Prevents cartesian products in complex joins

---

### 3. **MapStruct with Null-Safe Updates**

```java
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
```

**Why IGNORE null strategy?**
- When updating, only non-null fields from request should update the entity
- Allows partial updates (don't have to send all fields)
- If client sends `null`, it's ignored (not cleared)

**Example:**
```java
// Request only updates the name
DealUpdateRequest request = new DealUpdateRequest();
request.setName("New Name");
request.setAmount(null);  // This won't touch the amount in DB

dealMapper.updateEntity(request, deal);  // amount remains unchanged
```

---

### 4. **RBAC Scope Filtering**

```java
String scope = permissionEvaluatorService.getAccessScope(userId, tenantId, "deal", "read");

// Scope determines visibility:
// "ALL"  → Can see all deals
// "TEAM" → Can see own + team members' deals  
// "OWN"  → Can see only own deals
```

**Implementation in Specification:**
```java
visibleToUser(scope, userId, teamUserIds) {
    return (root, query, cb) -> switch (scope) {
        case "ALL" -> cb.conjunction();  // No restrictions
        case "TEAM" -> cb.or(
            root.get("ownerId").in(teamUserIds),
            cb.equal(root.get("ownerId"), userId),
            cb.equal(root.get("createdBy"), userId)
        );
        case "OWN" -> cb.or(
            cb.equal(root.get("ownerId"), userId),
            cb.equal(root.get("createdBy"), userId)
        );
    };
}
```

**Benefits:**
- Automatic, transparent filtering
- No RBAC logic leaks into business code
- Consistent across all list operations

---

### 5. **Activity Tracking Pattern**

Two-level activity tracking:

```
Global Activities (activities table)
└── Generic entity tracking across system
    └── Useful for: admin dashboards, system-wide audit

Deal Activities (deal_activities table)
└── Deal-specific timeline
    └── Useful for: deal history, timeline view, deal notes
```

**Implementation:**
```java
// When deal is created
logDealActivity(tenantId, dealId, "DEAL_CREATED", 
    "Deal created", userId, metadata);

// Logs to deal_activities with rich metadata
DealActivity activity = DealActivity.builder()
    .tenantId(tenantId)
    .dealId(dealId)
    .activityType("DEAL_CREATED")
    .description("Deal created")
    .performedBy(userId)
    .metadata(Map.of("name", "...", "stageId", "..."))
    .build();
dealActivityRepository.save(activity);
```

**Benefits:**
- Complete audit trail
- Separate concerns (global vs. entity-specific)
- Flexible metadata storage (JSONB)
- Easy to query activity history

---

### 6. **Custom Fields via JSONB (Not EAV)**

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private Map<String, Object> customData;
```

**Why JSONB and not EAV pattern?**

**EAV (Entity-Attribute-Value) Anti-pattern:**
```sql
-- BAD: Normalized but slow
deal_custom_fields
├── deal_id, attribute_key, attribute_value
└── Requires many joins for each custom field query (N+1 problem)
```

**JSONB Pattern:**
```sql
-- GOOD: Flexible and fast
deals
├── ...
├── custom_data = {"field1": "value1", "field2": "value2"}
└── Single row, single query, GIN indexable
```

**Benefits:**
- No N+1 queries
- Natural JSON support in PostgreSQL
- GIN index for efficient querying
- Easy to extend without schema changes
- Future migration to MetadataDefinition possible

**Storage Example:**
```json
{
  "industry": "Technology",
  "company_size": "Enterprise",
  "decision_makers": 3,
  "budget_approved": true,
  "implementation_timeline": "Q3 2026"
}
```

---

### 7. **Service Layer Responsibilities**

```java
@Service
@RequiredArgsConstructor
@Transactional
public class DealService {
    // Responsibilities:
    
    // 1. Business Logic
    - Validate business rules
    - Enforce constraints
    - Prevent invalid operations
    
    // 2. Entity Management
    - Load entities from repository
    - Set required fields
    - Track changes
    
    // 3. DTO Conversion
    - Map DTO → Entity (creation)
    - Map Entity → DTO (response)
    - Map DTO → Entity (updates)
    
    // 4. Cross-cutting Concerns
    - RBAC/visibility filtering
    - Activity tracking
    - Multi-tenancy enforcement
    
    // 5. Transactions
    - Coordinate multiple operations
    - Ensure consistency
}
```

**Key Pattern - Service shouldn't:**
- Know about HTTP (that's controller's job)
- Mix DTOs and entities carelessly
- Allow direct entity exposure
- Skip validation

---

### 8. **Transaction Boundaries**

```java
@Service
@Transactional  // Applied at class level
public class DealService {
    
    // Write operations inherit @Transactional
    public DealResponse createDeal(...) {
        // Transactional by default
    }
    
    // Read operations explicitly marked readonly
    @Transactional(readOnly = true)
    public Page<DealResponse> listDeals(...) {
        // Spring can optimize for read-only
        // No changelog tracking needed
    }
}
```

**Benefits:**
- Consistent transaction handling
- Better performance for read-only operations
- Automatic rollback on exceptions
- Prevents dirty reads

---

### 9. **Soft Delete Pattern**

```sql
-- Tables include soft delete fields
deleted BOOLEAN DEFAULT FALSE,
deleted_at TIMESTAMP,
deleted_by UUID

-- Queries automatically filter out deleted records
-- This can be enforced with Hibernate filters:
@SQLDelete(sql = "UPDATE deals SET deleted = true, deleted_at = NOW(), deleted_by = ?3 WHERE id = ?1 AND tenant_id = ?2")
@Where(clause = "deleted = false")
```

**Why include but not yet use?**
- Prepared for audit compliance
- Can restore deleted records
- Non-destructive delete for production safety

---

### 10. **Pagination Strategy**

```java
// Spring Data handles pagination
Pageable pageable = PageRequest.of(page, size);
Page<Deal> deals = dealRepository.findAll(spec, pageable);

// Response includes metadata
Map<String, Object> meta = Map.of(
    "page", deals.getNumber(),        // Current page (0-indexed)
    "size", deals.getSize(),          // Page size
    "total", deals.getTotalElements(),// Total records
    "totalPages", deals.getTotalPages()// Number of pages
);
```

**Benefits:**
- Efficient database queries (LIMIT/OFFSET)
- Predictable response sizes
- Client can navigate paginated results
- Prevents "fetch all" performance issues

---

### 11. **Error Handling Pattern**

```java
// Business rule violation
if (deal.getIsWon()) {
    throw new BusinessException("ALREADY_WON", "Deal already marked as won");
}

// Not found
dealRepository.findByIdAndTenantId(id, tenantId)
    .orElseThrow(() -> new RuntimeException("Deal not found"));

// Validation
if (request.getStageId() == null) {
    // DTO validation handles this via @NotNull
}
```

**Pattern:**
- `@NotNull/@NotBlank` for DTO validation (400 Bad Request)
- `BusinessException` for constraint violations (400 Bad Request)
- `RuntimeException` for system errors (500 Internal Server Error)

**Improvement opportunity:** Create custom exception hierarchy:
```java
class DealException extends RuntimeException { ... }
class DealAlreadyWonException extends DealException { ... }
class DealNotFound extends DealException { ... }
```

---

### 12. **Tenancy Enforcement at Three Levels**

```java
// Level 1: Database Constraints
CONSTRAINT fk_deals_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)

// Level 2: Repository Queries
Optional<Deal> findByIdAndTenantId(UUID id, UUID tenantId);
// Always include tenantId in WHERE clause

// Level 3: Service Logic
dealRepository.findByIdAndTenantId(id, tenantId)
    .orElseThrow(() -> new RuntimeException("Deal not found"));
// If tenant tries to access another tenant's deal, returns "not found"

// Level 4: RBAC + Visibility
String scope = permissionEvaluatorService.getAccessScope(...);
// User can only see deals based on their role/scope
```

**Benefits:**
- Defense in depth
- Single tenant can't see other tenant's data
- Even if one level fails, others prevent data leakage

---

### 13. **Testing Strategy**

```java
// Unit Tests (Service layer)
- Test business logic in isolation
- Mock repositories
- Test RBAC filtering
- Test activity logging

// Integration Tests
- Test repository + database
- Test service + repository
- Test controller + service
- Test RBAC with real PermissionEvaluatorService

// API Tests
- Test HTTP endpoints
- Test request/response formats
- Test error responses
- Test pagination

// Performance Tests
- Test specification query performance
- Test pagination with large datasets
- Test JSONB filtering
```

---

### 14. **Database Index Strategy**

```sql
-- Strategic indexes for common queries

-- Multi-tenancy + Filtering
CREATE INDEX idx_deal_tenant ON deals(tenant_id);
CREATE INDEX idx_deal_stage_id ON deals(stage_id);
CREATE INDEX idx_deal_owner_user_id ON deals(owner_user_id);

-- Date-based queries
CREATE INDEX idx_deal_expected_close_date ON deals(expected_close_date);
CREATE INDEX idx_deal_created_at ON deals(created_at);

-- Status queries
CREATE INDEX idx_deal_is_won ON deals(tenant_id, is_won);
CREATE INDEX idx_deal_is_lost ON deals(tenant_id, is_lost);

-- JSONB queries (GIN index)
CREATE INDEX idx_deal_custom_data ON deals USING GIN(custom_data);

-- Relationships
CREATE INDEX idx_deal_account_id ON deals(account_id);
CREATE INDEX idx_deal_contact_id ON deals(contact_id);
CREATE INDEX idx_deal_lead_id ON deals(lead_id);
```

**Query Plan Benefit:**
```
Before: Seq Scan (slow, O(n))
After:  Index Scan (fast, O(log n))
```

---

### 15. **Swagger/OpenAPI Documentation**

```java
@Tag(name = "Deals", description = "Deal Management APIs")
@Operation(summary = "Create deal", description = "...")
@Parameter(description = "Deal UUID")
@RequestParam(required = false) String search
```

**Automatically generates:**
- Interactive API documentation at `/swagger-ui.html`
- OpenAPI spec at `/v3/api-docs`
- Discoverable by API clients
- Test endpoints directly from UI

---

## Design Patterns Used

| Pattern | Usage | Benefit |
|---------|-------|---------|
| **Repository** | Data access abstraction | Testability, database independence |
| **Specification** | Composable query building | Flexibility, reusability |
| **DTO** | Data transfer layer | API contract, validation |
| **Mapper** | Object transformation | Separation of concerns |
| **Service** | Business logic orchestration | Testability, reusability |
| **Dependency Injection** | Constructor injection | Testability, loose coupling |
| **Strategy** | RBAC scope filtering | Polymorphic access control |
| **Observer** | Activity tracking | Audit trails, event logging |
| **Builder** | Entity construction | Flexible object creation |
| **Template Method** | @Transactional, @Timed | Cross-cutting concerns |

---

## Performance Optimizations

| Optimization | Implementation | Impact |
|--------------|-----------------|--------|
| **Eager Loading** | Stage fetched eagerly | Prevents N+1 on stage access |
| **Lazy Loading** | Account/Contact lazy | Reduces memory for list queries |
| **Pagination** | PageRequest(page, size) | O(1) memory regardless of total |
| **Indexing** | Strategic DB indexes | O(log n) query performance |
| **JSONB Index** | GIN index on custom_data | Fast filtering on JSON fields |
| **Read-Only Transactions** | @Transactional(readOnly=true) | Prevents changelog overhead |
| **Connection Pooling** | HikariCP default | Efficient DB connection reuse |
| **Query Specification** | Criteria API | Prevents unnecessary joins |

---

## Security Considerations

1. **Multi-Tenancy Isolation**
   - Every query includes tenantId
   - Tenant context from JWT token
   - Cannot see other tenant's data

2. **RBAC Integration**
   - PermissionEvaluatorService validates permissions
   - Scope-based visibility (ALL/TEAM/OWN)
   - Cannot bypass through direct repository access

3. **Input Validation**
   - DTO validation annotations
   - Business rule validation in service
   - SQL injection prevented (parameterized queries)

4. **Data Exposure**
   - DTOs hide internal entity details
   - Sensitive fields can be masked
   - Audit fields included for compliance

---

## Scalability Considerations

1. **Horizontal Scalability**
   - Stateless services
   - No server-to-server communication
   - Database is single source of truth

2. **Database Scalability**
   - Proper indexing
   - Pagination enforced
   - JSONB for custom fields (no extra tables)

3. **Caching Potential**
   - Deal stages can be cached (infrequently changed)
   - User team list can be cached
   - Consider Redis for high-traffic scenarios

4. **Future Partitioning**
   - Sharding key: tenantId
   - Deals can be partitioned by tenantId
   - Stages can be partitioned by tenantId

---

## Evolution Path

### Phase 1 (Current)
- ✅ Basic CRUD operations
- ✅ Pipeline management
- ✅ Activity tracking
- ✅ Custom fields (JSONB)

### Phase 2
- [ ] Deal forecasting/analytics
- [ ] Pipeline automation
- [ ] Deal scoring
- [ ] Activity/notes management

### Phase 3
- [ ] Deal approval workflows
- [ ] Sales playbooks
- [ ] Advanced reporting
- [ ] Data migrations

### Phase 4
- [ ] AI-powered insights
- [ ] Churn prediction
- [ ] Integration with external systems
- [ ] Mobile app support

---

## Summary

The Deal module implements enterprise-grade patterns:
- **SOLID principles** - Single responsibility, open/closed
- **12-factor methodology** - Stateless, environment-driven
- **Domain-driven design** - Clear boundaries, rich models
- **Test-driven structure** - Mockable dependencies
- **Security by default** - Multi-tenancy at every level
- **Performance first** - Indexes, pagination, caching-ready

**Ready for production and future growth!**
