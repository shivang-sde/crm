# Deal Module - API Quick Reference

## Deal Stage Management

### Create Deal Stage
```http
POST /api/v1/deal-stages
Content-Type: application/json

{
  "name": "Qualification",
  "color": "#FF5733",
  "display_order": 1,
  "is_default": true,
  "is_closed": false
}
```

### List All Deal Stages
```http
GET /api/v1/deal-stages
```

### Get Deal Stage
```http
GET /api/v1/deal-stages/{id}
```

### Update Deal Stage
```http
PUT /api/v1/deal-stages/{id}
Content-Type: application/json

{
  "name": "Qualified",
  "color": "#00FF00",
  "display_order": 2
}
```

### Delete Deal Stage
```http
DELETE /api/v1/deal-stages/{id}
```

---

## Deal Management

### Create Deal
```http
POST /api/v1/deals
Content-Type: application/json

{
  "name": "Enterprise Solution Deal",
  "stage_id": "550e8400-e29b-41d4-a716-446655440000",
  "account_id": "550e8400-e29b-41d4-a716-446655440001",
  "contact_id": "550e8400-e29b-41d4-a716-446655440002",
  "lead_id": "550e8400-e29b-41d4-a716-446655440003",
  "amount": 150000.00,
  "expected_close_date": "2026-12-31",
  "probability": 75,
  "description": "Enterprise software license renewal",
  "owner_user_id": "550e8400-e29b-41d4-a716-446655440004",
  "custom_data": {
    "industry": "Technology",
    "company_size": "Enterprise",
    "decision_makers": 3
  }
}
```

### List Deals with Filtering
```http
GET /api/v1/deals?stage=550e8400-e29b-41d4-a716-446655440000&owner=550e8400-e29b-41d4-a716-446655440004&search=enterprise&won=false&lost=false&closeDateFrom=2026-01-01&closeDateTo=2026-12-31&page=0&size=20
```

**Query Parameters:**
- `search` - Search in name and description
- `stage` - Filter by stage UUID
- `accountId` - Filter by account UUID
- `contactId` - Filter by contact UUID
- `owner` - Filter by owner user UUID
- `won` - Filter by won status (true/false)
- `lost` - Filter by lost status (true/false)
- `closeDateFrom` - Expected close date from (YYYY-MM-DD)
- `closeDateTo` - Expected close date to (YYYY-MM-DD)
- `page` - Page number (0-indexed)
- `size` - Page size (default 20)

### Get Deal Details
```http
GET /api/v1/deals/{id}
```

### Update Deal
```http
PUT /api/v1/deals/{id}
Content-Type: application/json

{
  "name": "Enterprise Solution Deal - Updated",
  "amount": 200000.00,
  "probability": 85,
  "expected_close_date": "2026-11-30",
  "custom_data": {
    "industry": "Technology",
    "company_size": "Enterprise",
    "decision_makers": 4
  }
}
```

### Change Deal Stage
```http
PATCH /api/v1/deals/{id}/stage
Content-Type: application/json

{
  "stageId": "550e8400-e29b-41d4-a716-446655440010"
}
```

### Mark Deal as Won
```http
PATCH /api/v1/deals/{id}/won
```

### Mark Deal as Lost
```http
PATCH /api/v1/deals/{id}/lost
```

### Assign Deal to User
```http
PUT /api/v1/deals/{id}/assign
Content-Type: application/json

{
  "ownerUserId": "550e8400-e29b-41d4-a716-446655440005"
}
```

### Delete Deal
```http
DELETE /api/v1/deals/{id}
```

---

## Response Format

### Success Response
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenant_id": "550e8400-e29b-41d4-a716-446655440100",
    "name": "Enterprise Solution Deal",
    "stage": {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "tenant_id": "550e8400-e29b-41d4-a716-446655440100",
      "name": "Qualification",
      "color": "#FF5733",
      "display_order": 1,
      "is_default": true,
      "is_closed": false,
      "created_at": "2026-06-17T10:00:00Z",
      "updated_at": "2026-06-17T10:00:00Z"
    },
    "account_id": "550e8400-e29b-41d4-a716-446655440002",
    "contact_id": "550e8400-e29b-41d4-a716-446655440003",
    "lead_id": "550e8400-e29b-41d4-a716-446655440004",
    "amount": 150000.00,
    "expected_close_date": "2026-12-31",
    "probability": 75,
    "description": "Enterprise software license renewal",
    "owner_user_id": "550e8400-e29b-41d4-a716-446655440005",
    "is_won": false,
    "is_lost": false,
    "won_at": null,
    "lost_at": null,
    "custom_data": {
      "industry": "Technology",
      "company_size": "Enterprise",
      "decision_makers": 3
    },
    "created_by": "550e8400-e29b-41d4-a716-446655440006",
    "updated_by": "550e8400-e29b-41d4-a716-446655440006",
    "created_at": "2026-06-17T10:00:00Z",
    "updated_at": "2026-06-17T10:30:00Z"
  },
  "timestamp": "2026-06-17T10:30:00Z"
}
```

### List Response with Pagination
```json
{
  "success": true,
  "data": [
    { /* deal object 1 */ },
    { /* deal object 2 */ }
  ],
  "metadata": {
    "page": 0,
    "size": 20,
    "total": 45,
    "totalPages": 3
  },
  "timestamp": "2026-06-17T10:30:00Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "DUPLICATE",
    "message": "A stage with this name already exists for this tenant"
  },
  "timestamp": "2026-06-17T10:30:00Z"
}
```

---

## RBAC Scopes

The Deal module respects the following RBAC permission scopes:

### Permission: `deal:read`
- **ALL** - User sees all deals
- **TEAM** - User sees:
  - Deals they own
  - Deals owned by their team members (from team_user_ids)
  - Deals they created
- **OWN** - User sees only:
  - Deals they own
  - Deals they created

### Permission: `deal:write`
Required to create, update, and delete deals.

### Permission: `deal:delete`
Required to delete deals.

---

## Activity Types

Deal activities are logged to `deal_activities` table with these types:

| Activity Type | Event | Metadata |
|---------------|-------|----------|
| DEAL_CREATED | New deal created | name, stageId, amount |
| DEAL_UPDATED | Deal information modified | oldName, newName, oldStageId, newStageId |
| STAGE_CHANGED | Deal moved to different stage | oldStageId, oldStageName, newStageId, newStageName |
| DEAL_WON | Deal closed as won | amount |
| DEAL_LOST | Deal closed as lost | previousStage |
| OWNER_CHANGED | Deal assigned to different user | oldOwnerId, newOwnerId |

---

## Use Cases

### Create Pipeline for New Tenant
```bash
# 1. Create default stages
POST /api/v1/deal-stages { "name": "New", "is_default": true, "display_order": 0 }
POST /api/v1/deal-stages { "name": "Qualified", "display_order": 1 }
POST /api/v1/deal-stages { "name": "Proposal", "display_order": 2 }
POST /api/v1/deal-stages { "name": "Negotiation", "display_order": 3 }
POST /api/v1/deal-stages { "name": "Won", "is_closed": true, "display_order": 4 }
POST /api/v1/deal-stages { "name": "Lost", "is_closed": true, "display_order": 5 }
```

### Create Deal from Lead
```bash
# 1. Get lead details
GET /api/v1/leads/{lead_id}

# 2. Create account from lead
POST /api/v1/accounts { "name": "...", "lead_id": "{lead_id}" }

# 3. Create deal from lead
POST /api/v1/deals {
  "name": "Deal from {lead.name}",
  "lead_id": "{lead_id}",
  "account_id": "{account_id}",
  "stage_id": "{default_stage_id}"
}
```

### Search Deals
```bash
# Find all enterprise deals in negotiation stage
GET /api/v1/deals?search=enterprise&stage={negotiation_stage_id}

# Find open deals closing in Q4 2026
GET /api/v1/deals?won=false&lost=false&closeDateFrom=2026-10-01&closeDateTo=2026-12-31

# Find my team's open deals
GET /api/v1/deals?owner={manager_user_id}
```

### Move Deal Through Pipeline
```bash
# User moves deal from Qualified → Proposal
PATCH /api/v1/deals/{id}/stage { "stageId": "{proposal_stage_id}" }

# User moves deal to Negotiation
PATCH /api/v1/deals/{id}/stage { "stageId": "{negotiation_stage_id}" }

# Deal won!
PATCH /api/v1/deals/{id}/won

# Deal lost (at negotiation stage)
PATCH /api/v1/deals/{id}/lost
```

### Reassign Deals
```bash
# Manager reassigns sales rep's deals to another team member
PUT /api/v1/deals/{id1}/assign { "ownerUserId": "{new_owner_id}" }
PUT /api/v1/deals/{id2}/assign { "ownerUserId": "{new_owner_id}" }
```

---

## Common Filters

### All Deals for an Account
```
GET /api/v1/deals?accountId=550e8400-e29b-41d4-a716-446655440000
```

### My Open Deals
```
GET /api/v1/deals?owner={my_user_id}&won=false&lost=false
```

### Team Pipeline
```
GET /api/v1/deals?owner={manager_user_id}
```

### Deals Closing This Quarter
```
GET /api/v1/deals?closeDateFrom=2026-10-01&closeDateTo=2026-12-31&won=false&lost=false
```

### Won Deals This Year
```
GET /api/v1/deals?won=true&closeDateFrom=2026-01-01&closeDateTo=2026-12-31
```

---

## Error Codes

| Code | Status | Description |
|------|--------|-------------|
| DUPLICATE | 400 | Stage name already exists |
| ALREADY_WON | 400 | Deal already marked as won |
| ALREADY_LOST | 400 | Deal already marked as lost |
| IN_USE | 400 | Stage cannot be deleted (deals exist) |
| NOT_FOUND | 404 | Resource not found |
| FORBIDDEN | 403 | User lacks permission |
| VALIDATION_ERROR | 400 | Invalid request data |

---

## Integration Example: Java Client

```java
// Get all open deals for a user
Page<DealResponse> deals = dealService.listDeals(
    tenantId,
    null,                          // stageId
    null,                          // accountId
    null,                          // contactId
    userId,                        // ownerUserId
    null,                          // searchTerm
    false,                         // isWon
    false,                         // isLost
    null, null,                    // date range
    0, 20                          // pagination
);

// Change stage and log it
DealResponse updated = dealService.changeStage(dealId, tenantId, newStageId, userId);

// Mark as won
DealResponse won = dealService.markDealWon(dealId, tenantId, userId);
```

---

## Troubleshooting

### "Stage not found"
- Ensure `stage_id` exists for your tenant
- Verify you're using the correct UUID format
- Check that the stage hasn't been deleted

### "Deal not found"
- Verify the deal belongs to your tenant
- Check RBAC scope (might be hidden due to OWN/TEAM scope)
- Ensure the deal hasn't been deleted

### "Duplicate stage name"
- Stage names must be unique per tenant
- Try a different name or check existing stages

### "Cannot delete stage"
- Ensure no deals are using this stage
- Move deals to different stage first

---

**Last Updated:** 2026-06-17
**Version:** 1.0 Production
