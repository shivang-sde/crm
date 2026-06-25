1. Phase 3 items (from your guide — still open)
Lead conversion — DB has isConverted / convertedAt, but there’s no dedicated convert API or UI (“Convert to contact/account”). Needs backend endpoint + detail action (and later link to Contact/Account modules).
Bulk operations — Multi-select on list: bulk assign, bulk delete, bulk status change (needs lead:delete / lead:assign in UI).
Export — CSV/Excel for filtered leads (lead:export permission exists in DB, not wired in frontend).
2. RBAC polish
Permissions exist in seed data but aren’t fully used in UI:

lead:delete — only generic edit path today
lead:assign — assign works but not gated separately
lead:export — not implemented
Scope — getLeadScope() in usePermissions isn’t applied (e.g. “own leads only” for employees)
3. UX / data gaps
Filter persistence — URL query params or Zustand (planned in docs, not done).
Sources admin — API lists active only; inactive sources disappear from settings (backend: “list all” for admin).
LeadDetailResponse — backend has a richer DTO; frontend still loads lead + separate activities/notes (fine, but could be one call).
Dashboard widgets — e.g. recent unconverted leads (getRecentUnconvertedLeads on backend, no UI).



Backend (lead module) still on the architecture doc
These were described in lead_module.md but aren’t fully there as product features:

Convert lead workflow (event → contact/account)
Domain events (LeadCreatedEvent, etc.) for automation
Duplicate detection (email/phone queries exist in repository; no API/UI)
Workflow integration (workflow permissions exist; no lead triggers UI)


permission + scope

That scales to unlimited custom roles and matches how mature CRMs like Salesforce, HubSpot, and Zoho CRM handle record visibility. The role grants permissions, and the permission carries a visibility scope (OWN, TEAM, ALL).


for select and option purposes we need to develop an api for accounts, contact in order to convert leads and show all the exting records wihtouht scope

GET /accounts/lookup
GET /contacts/lookup