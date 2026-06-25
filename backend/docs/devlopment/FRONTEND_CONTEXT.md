# FRONTEND_CONTEXT.md

## Frontend Stack

* Next.js 16.x
* TypeScript
* Tailwind CSS
* shadcn/ui
* TanStack Table
* React Query (@tanstack/react-query)
* React Hook Form
* Zod Validation
* App Router

---

# Development Philosophy

Frontend development starts only after backend APIs are completed and validated.

Frontend must follow backend contracts.

Do not redesign backend API structures.

Reuse existing UI patterns whenever possible.

Consistency is preferred over innovation.

---

# Project Structure

src

├── app
├── components
├── hooks
├── lib
├── constants
├── providers
├── store
└── types

---

# API Layer Standards

All API communication belongs in:

lib/

Examples:

* lib/leads.ts
* lib/accounts.ts
* lib/contacts.ts
* lib/deals.ts

API functions should return unwrapped ApiResponse data.

No API logic inside UI components.

---

# Type Standards

Create module-specific types.

Examples:

* Lead
* LeadDetail
* CreateLeadRequest
* UpdateLeadRequest

Keep frontend types aligned with backend DTOs.

---

# React Query Standards

Each module should expose hooks for all CRUD operations.

Examples:

useLeads()
useLead()

useCreateLead()
useUpdateLead()
useDeleteLead()

useAccounts()
useAccount()

useCreateAccount()
useUpdateAccount()
useDeleteAccount()

Apply cache invalidation consistently.

---

# Form Standards

Use:

* React Hook Form
* Zod Validation

Requirements:

* Shared form components
* Reusable validation schemas
* Backend validation compatibility

---

# List Page Standards

All list pages must support:

* Server-side pagination
* Search
* Filtering
* Sorting
* Bulk selection
* Bulk actions

Use:

TanStack Table

---

# Permission Standards

UI must respect permissions.

Examples:

* lead:read

* lead:write

* lead:delete

* account:read

* account:write

* account:delete

Actions should not render if permission is missing.

Scope restrictions must be respected.

---

# Create/Edit Standards

Create and Edit experiences should use:

* Dialog or Drawer pattern
* Shared form components
* React Hook Form
* Zod schemas

Support custom fields when available.

---

# Detail Page Standards

Detail pages should contain:

## Main Information

Core record details.

## Related Records

Related accounts, contacts, deals, etc.

## Activity Timeline

Activities associated with the record.

## Notes

User-created notes.

## Audit Information

Created by, updated by, timestamps.

---

# Dynamic Metadata Support

Future modules may use:

* MetadataDefinition
* MetadataValue

Forms and filters should be generated dynamically where possible.

Examples:

* Deal Stage
* Deal Type
* Task Priority
* Ticket Status

---

# Dynamic Custom Fields

Modules may expose custom field definitions.

Frontend should:

* Render fields dynamically.
* Submit values into customData.
* Display values in detail pages.

Do not hardcode custom field structures.

---

# State Management Standards

Use React Query for server state.

Use local component state when possible.

Avoid unnecessary global state.

---

# UI Standards

Use:

* shadcn/ui components
* Tailwind CSS utilities

Maintain consistency with existing modules.

Avoid introducing new UI frameworks.

---

# Frontend Development Workflow

For every completed backend module:

1. Types
2. API Layer
3. React Query Hooks
4. List Page
5. Create Dialog
6. Edit Dialog
7. Detail Page
8. Permission Integration
9. Testing

Frontend implementation must remain aligned with backend contracts and RBAC rules.
