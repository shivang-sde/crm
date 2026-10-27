# Sales CRM and customer lifecycle management.


**Think of a CRM as managing three different lifecycles**
1. Prospect Lifecycle (Before customer)
2. Sales Lifecycle (Closing business)
3. Customer Lifecycle (After sale)

Most people think CRM ends when a deal is won.
In reality, *that's only the middle of the journey*.

## Phase 1 — Prospect Lifecycle
This is where marketing and sales begin.

Marketing Campaign
        │
        ▼
       Lead
        │
   Qualification
        │
        ▼
    Interested?
    │       │
   No      Yes
   │         │
Archive     Contact

***Typical CRM modules involved***
Lead
Lead Source
Campaign
Activities
Tasks
Calls
Meetings
Emails
Notes

Questions CRM answers:

Who is this person?
Where did they come from?
Are they qualified?
Who owns them?
What follow-up is pending?


## Phase 2 — Sales Lifecycle

Now the lead is serious.
They become an Account + Contact.
Then the salesperson starts negotiating.
      Lead
        │
     Convert
        │
 ┌─────────────┐
 │   Account   │
 │   Contact   │
 └─────────────┘
        │
        ▼
      Deal

Now the deal moves through stages.

Prospecting
↓
Qualification
↓
Demo
↓
Proposal
↓
Negotiation
↓
Verbal Approval
↓
Contract Sent
↓
Closed Won

or

Closed Lost

During this phase the CRM tracks

Pipeline
Probability
Forecast
Revenue
Activities
Documents
Approvals
Quotes

This is where your current CRM is already very strong.

## Phase 3 — Fulfillment / Delivery
This is where many custom CRMs stop.
But businesses don't stop here.
Imagine selling:
CRM Software
OR
Website
OR
Insurance
OR
Maintenance Contract
OR
Training

Winning the deal doesn't mean work is over.

Now somebody must deliver.
Won Deal
↓
Products
↓
Services
↓
Projects
↓
Licenses
↓
Subscriptions

Some companies have:
Project Module, Service Orders, Installations, Contracts, Contracts, Assets

It depends on industry.

## Phase 4 — Customer Lifecycle
Now customer is active.
Customer
↓
Uses Service
↓
Support
↓
Invoices
↓
Renewals
↓
Upsell
↓
Cross Sell
↓
Retention

CRM now asks
Is customer happy?
Need support?
Need renewal?
Need upgrade?
Need another product?


Entire CRM lifecycle
Marketing
     │
     ▼
    Lead
     │
Qualification
     │
    Convert
     ▼
    Account
    Contact
     │
     ▼
    Deal
     │
Negotiation
     │
     ▼
Closed Won
     │
     ▼
Products / Services
     │
     ▼
Customer Entitlements
     │
     ▼
Support
     │
     ▼
Renewal
     │
     ▼
Upsell
     │
     ▼
Another Deal

A customer may generate revenue for 10 years.

## Generic CRM architecture

Prospecting
------------------
Lead
Campaign
Activity

Sales
------------------
Account
Contact
Deal
Quote
Deal Line Items

Customer
------------------
Offering Catalog
Customer Entitlements
Contracts
Assets

Service
------------------
Cases
Tickets
Knowledge Base

Operations
------------------
Projects
Tasks
Meetings
Calls

Revenue
------------------
Invoices
Payments
Subscriptions
Renewals

Communication
------------------
Email
SMS
WhatsApp
Calling

Automation
------------------
Workflow
Reminder
Recurrence
Notifications

Analytics
------------------
Reports
Dashboards
Forecasts


CRM as a long-term SaaS product, I'd think of it in three maturity levels:

Level 1 – Sales CRM (your current foundation):

Leads
Contacts
Accounts
Deals
Activities

Level 2 – Revenue CRM (the next logical step):

Offering Catalog
Deal Line Items
Quotes
Customer Entitlements
Renewals

Level 3 – Customer Success CRM:

Support/Tickets
Contracts
Projects/Implementations
Assets
Customer Health
Renewals and Upsells






CRM Workflow Engine will support both first-class CRM actions and generic external HTTP automation. Generic HTTP is not constrained to pre-registered providers. Managed connectors are an optimization and UX layer on top of the generic integration capability, not a prerequisite for integrating an external API.

┌─────────────────────────────────────────────┐
│                WORKFLOW ENGINE              │
│                                             │
│ Trigger / Condition / Action / Wait / etc. │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│             ACTION EXECUTION                │
│                                             │
│ CRM Actions | HTTP Request | Connectors     │
└──────────────┬────────────────┬─────────────┘
               │                │
       ┌───────▼───────┐ ┌──────▼──────────┐
       │ CRM Services  │ │ Integration      │
       │               │ │ Layer            │
       └───────────────┘ │                  │
                         │ Generic HTTP     │
                         │ Managed Connect. │
                         └────────┬─────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │ Outbound HTTP / Connector │
                    │ Security + Credentials    │
                    └─────────────┬─────────────┘
                                  │
                           External Internet

Lead
  ↓
Account + Contact
  ↓
Deal / Opportunity
  ↓
Commercial Execution
  ├── Quote
  ├── Quote revision/version
  ├── Quote acceptance/rejection
  ├── Order / commercial commitment
  └── Invoice / payment lifecycle

## Customer Health - Business Logic & Decision Rules

Customer Health in this CRM is a set of deterministic risk signals, not a health
score. Each signal is a fact derived from an existing source record (a Feedback
row, Ticket, Customer Entitlement, or Deal). The signal names that source row so
a user can open it and check the claim. There is no weighted score, no churn
probability, no AI prediction, and no aggregation of signals into a single
"healthy" or "at risk" state.

An empty result means only that none of the rules below currently applies. It
does not mean the customer is healthy, engaged, or safe from churn.

### API surface

| Endpoint | Purpose | Parameters | Paging |
| --- | --- | --- | --- |
| `GET /api/v1/accounts/{accountId}/health/risk` | Signals for one account | `from`, `to` | None. All matching signals are returned. |
| `GET /api/v1/customer-health/risk-signals` | Signals across accounts for triage | `from`, `to`, `type`, `severity`, `page`, `size` | Server accepts `page` (default 0) and `size` (default 20, max 100). Type/severity filtering, global sorting, exact count, and paging happen in PostgreSQL before the page is returned. |

Both endpoints require `account:read`. They do not create a new write or
analysis permission.

The `account:read` check is not only the endpoint gate. The account route
verifies that the parent account is visible in the caller's record scope before
evaluating any signal: an unknown or other-tenant account fails with
`NOT_FOUND`, and an existing account outside that scope fails as forbidden. The cross-account
route applies the same `ALL`, `TEAM`, or `OWN` account scope in SQL and returns
only signals attached to visible accounts. A signal therefore requires both
parent-account visibility and its source domain's read scope.

A deal or entitlement with no linked account has no parent account to
authorize, so it remains governed only by its source-domain scope and carries a
null `accountId`. Inside each source domain the read scope is resolved separately:

- feedback-derived signals require `feedback:read`;
- ticket-derived signals require `ticket:read`;
- entitlement-derived signals require `entitlement:read`;
- deal-derived signals require `deal:read`.

If a caller lacks one of those domain permissions, that domain contributes no
signals. The request still succeeds for the domains the caller can read; it does
not fail with a partial-data error. The global Customer Health page and the
Account Health panel show a notice naming any unreadable source domains, so an
incomplete evaluation is not presented as a complete one. Within each domain the
normal `ALL`, `TEAM`, and `OWN` row scope applies, so a signal can only come from
a row the caller could already list through that domain. Tenant isolation and
soft-delete exclusion apply to every source query.

The response contains `accountId`, `type`, `severity`, `reason`, `detectedAt`,
`sourceType`, `sourceId`, and optional rule-specific `metadata`. `detectedAt` is
the timestamp of the underlying fact where the source has one; it is never the
time the request was made. Severity is fixed per signal type, not computed from
the row's values.

On the cross-account endpoint, signals are sorted by severity (`HIGH` before
`MEDIUM`), then by `detectedAt` newest first, then by `sourceId` and signal type
as stable tiebreakers. `total` and `totalPages` describe the filtered result
over the whole tenant-visible set, not just the returned page. The cross-account
response returns `accountId` but no account name. `accountId` is nullable but
always present: it is serialized as JSON `null`, never omitted, for a deal or
entitlement source that has no linked account.

### Reporting window and shared limits

`from` and `to` are ISO-8601 instants and form a half-open window `[from, to)`:

- neither supplied: `[now - 30 days, now)`;
- only `from`: `to` is now;
- only `to`: `from` is `to - 30 days`;
- `from` must be strictly before `to`;
- the range must be less than 367 days.

Invalid or inverted ranges are rejected before any rule runs. The window applies
to the four scored/complaint feedback rules, the feedback-silence rule (using
`from` only), and recent deal losses. The SLA, support-health, and entitlement
rules use the current clock instead and do not filter on `from`/`to`; an invalid
range is still rejected for those requests because validation happens first.

The old per-source 500-row ceiling has been removed. The cross-account endpoint
evaluates every matching source row in PostgreSQL, applies type/severity filters
there, and only then counts and pages the final signal set. The account-scoped
endpoint remains unpaginated and materializes all matching signals in memory;
that is a response-size/memory limit, not a silent row cap. Any limit imposed by
an upstream proxy or client is visible as a transport limit, not as omitted
signals inside this contract.

A source row can produce one signal per applicable rule. The same row does not
produce two copies of the same rule. Different source rows produce separate
signals; nothing is collapsed into an account-level count.

### Feedback-derived rules

The four feedback-derived rules share these requirements:

- the Feedback row is not soft-deleted and belongs to the current tenant;
- its `source` is non-null and is not `INTERNAL` (so `WEBSITE`, `TYPEFORM`,
  `GOOGLE_FORMS`, `WHATSAPP`, `EMAIL`, and `API` count as customer-originated;
  `INTERNAL` and unattributed/legacy null-source rows never count);
- its `createdAt` falls inside `[from, to)`;
- the caller can read the row under `feedback:read` scope;
- `sourceType` is `FEEDBACK`, `sourceId` is the Feedback row id, and
  `detectedAt` is the feedback's `createdAt`;
- every visible matching feedback row is evaluated; there is no per-rule 500-row cutoff.

No score threshold is applied to `GENERAL_FEEDBACK`, and scoreless feedback is
never promoted into a scored rule.

#### NEGATIVE_NPS (HIGH)

Fires when a customer-originated Feedback row has type `NPS` and a score from
0 through 6 inclusive. NPS 7-10 does not fire.

- Event timestamp: the Feedback row's `createdAt`.
- Evidence: feedback type, score, comment when present, and correlated ticket
  id when present.
- Limits: this is a single-response detractor signal, not an account NPS score.
  Multiple negative responses create multiple signals. A response from a
  non-customer source, a deleted row, or a row outside the reporting window is
  excluded. A customer who gave a negative response but has no Feedback source
  recorded (null source) is also excluded.
- Proof: `RiskSignalPersistenceIntegrationTest#feedbackRulesApplyTheirTypeScoreAndWindowOnRealRows`
  pins the 0-6 predicate and the window boundaries;
  `RiskSignalControllerTest#ruleThresholdsMatchTheDocumentedScales` pins the
  constant; `RiskSignalPersistenceIntegrationTest#internalAndUnattributedFeedbackDoNotFeedCustomerRiskRules`
  pins source exclusion.

#### LOW_CSAT (MEDIUM)

Fires when a customer-originated Feedback row has type `CSAT` and a score of 1
or 2. CSAT 3-5 does not fire.

- Event timestamp: the Feedback row's `createdAt`.
- Evidence: feedback type, score, comment when present, and correlated ticket
  id when present.
- Limits: one low rating produces one signal; it is not averaged into an
  account CSAT score. The same source, delete, scope, and window exclusions as
  the other feedback rules apply. A CSAT of 3 is treated as neutral for this
  rule even though it may be considered a low score by another business policy.
- Proof: `RiskSignalPersistenceIntegrationTest#feedbackRulesApplyTheirTypeScoreAndWindowOnRealRows`
  and `RiskSignalControllerTest#ruleThresholdsMatchTheDocumentedScales`.

#### HIGH_CES (MEDIUM)

Fires when a customer-originated Feedback row has type `CES` and a score from 4
through 7 inclusive. CES 1-3 does not fire.

- Event timestamp: the Feedback row's `createdAt`.
- Evidence: feedback type, score, comment when present, and correlated ticket
  id when present.
- Limits: CES is inverted relative to satisfaction: a higher number means more
  effort, so 4 and above is treated as high effort. This is still a single
  response, not an account effort score. The shared source, delete, scope, and
  window exclusions apply.
- Proof: `RiskSignalPersistenceIntegrationTest#feedbackRulesApplyTheirTypeScoreAndWindowOnRealRows`
  and `RiskSignalControllerTest#ruleThresholdsMatchTheDocumentedScales`.

#### RECENT_COMPLAINT (HIGH)

Fires when a customer-originated Feedback row has type `COMPLAINT` inside
`[from, to)`. The complaint's score is ignored; complaints are validated as
unscored.

- Event timestamp: the Feedback row's `createdAt`.
- Evidence: feedback type and comment when present; the source id is the
  Feedback row.
- Limits: the rule records that a complaint was submitted. It does not grade
  severity, confirm investigation, or guarantee a support ticket exists. Several
  complaints produce several signals. Internal, null-source, deleted, and
  out-of-window complaints are excluded.
- Proof: `RiskSignalPersistenceIntegrationTest#feedbackRulesApplyTheirTypeScoreAndWindowOnRealRows`
  and `RiskSignalPersistenceIntegrationTest#internalAndUnattributedFeedbackDoNotFeedCustomerRiskRules`.

### Engagement rule

#### ENGAGEMENT_FEEDBACK_SILENT (MEDIUM)

Fires once per account when the account has at least one visible,
customer-originated Feedback row and its most recent such row was created
strictly before `from`. In other words: the customer has demonstrated that they
can give feedback, but no customer-originated feedback is on record in or after
the reporting window.

- `to` is not used by this rule.
- Event timestamp: the latest Feedback row's `createdAt`. It can be older than
  the reporting window.
- Evidence: `lastFeedbackAt`, `lastFeedbackType`, `windowStart`, and
  `daysSinceLastFeedback` (days before the window start, not days before now).
- Exclusions: an account with no customer-originated feedback at all raises
  nothing; internal or null-source feedback does not count; deleted and
  out-of-scope rows do not count; recent customer feedback suppresses the
  signal.
- Limits: this asserts silence in the feedback channel, not disengagement. A
  customer can be silent because no survey was sent, because they prefer another
  channel, or because a newer feedback row is not visible to this caller. The
  CRM has no inbound email, portal, web-analytics, or product-usage stream that
  would support a broader engagement claim. If the latest visible feedback is
  internal and an older customer-originated row exists, the older row can still
  make the account appear silent.
- Proof: `RiskSignalPersistenceIntegrationTest#feedbackSilenceIsEvaluatedPerAccountAndIgnoresInternalRows`;
  `RiskSignalControllerTest#silenceIsOnlyReportedForAnAccountThatGaveFeedbackBefore`,
  `#anAccountThatNeverGaveFeedbackRaisesNoEngagementSignal`, and
  `#recentFeedbackSuppressesTheSilenceSignal`.

### Ticket and support rules

Ticket rules use the current clock (`now`) and do not honor `from`/`to`.
Every matching ticket is evaluated; there is no per-rule 500-row cutoff. All
ticket sources must be non-deleted, tenant-owned, and visible through the
caller's `ticket:read` scope.

#### SLA_BREACHED_TICKET (HIGH)

Fires when the Ticket's persisted `slaBreached` flag is true. This reads the
flag; it does not recompute a breach from the deadline fields.

- Status is unrestricted: a ticket that was breached and later closed or
  resolved still produces this historical signal.
- Event timestamp: the Ticket's `createdAt`. There is no separate breach
  timestamp stored or exposed.
- Evidence: ticket subject, status, response deadline, resolution deadline,
  first-response time, resolved time, and closed time when present.
- Limits: the flag is set once by the SLA process and is not cleared when the
  ticket closes, so this is not a count of currently open breaches. A ticket
  whose deadline has passed but whose flag has not yet been stamped does not
  produce this signal. A wrong or missing deadline snapshot on the ticket can
  make the flag itself misleading.
- Proof: `RiskSignalControllerTest#everyRuleEmitsASignalForAStagedRow` and
  `#signalsCarryTheAccountAndSourceTheyCameFrom`.

#### SUPPORT_NO_RESPONSE_PAST_DUE (HIGH)

Fires for a ticket that is still `OPEN` or `IN_PROGRESS` when all of the
following are true:

- `firstResponseAt` is null;
- `responseDueAt` is non-null;
- `now` is at or after `responseDueAt`.

- Event timestamp: the Ticket's `createdAt`, not the missed deadline.
- Evidence: ticket subject, status, both deadlines, first-response time, and
  resolution timestamps.
- Limits: this means no first response is recorded in the CRM. A reply sent
  through a channel that does not stamp `firstResponseAt` can produce a false
  positive. A ticket already answered after its deadline is reported as
  `SUPPORT_SLOW_FIRST_RESPONSE` instead, not as "no response". Resolved and
  closed tickets are excluded.
- Proof: `RiskSignalControllerTest#noResponsePastTheTicketsOwnDeadlineIsReported`
  and `#closedTicketsRaiseNoSupportSignals`.

#### SUPPORT_SLOW_FIRST_RESPONSE (MEDIUM)

Fires for a ticket that is still `OPEN` or `IN_PROGRESS` when
`firstResponseAt` is present, `responseDueAt` is present, and
`firstResponseAt` is strictly after `responseDueAt`.

- Event timestamp: the recorded `firstResponseAt`.
- Evidence: ticket subject, status, both deadlines, first-response time, and
  resolution timestamps.
- Limits: a response exactly at the deadline is not late. Missing either
  timestamp suppresses the rule rather than guessing. This reports the lateness
  of the first recorded response; it does not judge the quality of that
  response. Resolved and closed tickets are excluded.
- Proof: `RiskSignalControllerTest#answeredAfterTheResponseDeadlineIsReportedAsSlowNotMissing`
  and `#answeredBeforeTheDeadlineRaisesNoSupportSignal`.

#### SUPPORT_PAST_RESOLUTION_DUE (MEDIUM)

Fires for a ticket that is still `OPEN` or `IN_PROGRESS` when
`resolutionDueAt` is non-null and `now` is at or after that deadline.

- Event timestamp: the Ticket's `createdAt`, not the missed resolution deadline.
- Evidence: ticket subject, status, both deadlines, first-response time, and
  resolution timestamps.
- Limits: this can coexist with a response signal on the same ticket. A ticket
  that is resolved or closed is excluded even if its old resolution deadline has
  passed. The signal does not claim the customer is waiting for an update, only
  that resolution is overdue by the ticket's own persisted deadline.
- Proof: `RiskSignalControllerTest#openTicketPastItsOwnResolutionDeadlineIsReported`
  and `#closedTicketsRaiseNoSupportSignals`.

### Entitlement rules

Entitlement rules use the current UTC date and ignore `from`/`to`. A date here
is a calendar date; "today" is the current date in UTC. All entitlement sources
must be non-deleted, tenant-owned, and visible through the caller's
`entitlement:read` scope.

"Live coverage" in this section means an entitlement whose status is `ACTIVE`
or `PENDING` and whose `endDate` is non-null and not before today. `PENDING`
counts because a forward-dated renewal is provisioned as `PENDING` and may not
be promoted by a scheduler. `SUSPENDED`, `CANCELLED`, `TERMINATED`, and
`EXPIRED` are not live coverage.

#### ENTITLEMENT_APPROACHING_EXPIRY (MEDIUM)

Fires for an `ACTIVE` entitlement whose `endDate` is today or later and which
satisfies either condition:

- `endDate` is at or before `today + 30 days`; or
- the entitlement is renewable, has a non-null `renewalDueDate`, and
  `renewalDueDate` is between today and `today + 30 days` inclusive.

A single entitlement produces at most one signal even when both conditions
hold.

- Event timestamp: the entitlement's `createdAt`.
- Evidence: status, start date, end date, renewal due date, auto-renew flag,
  renewable flag, and offering id.
- Limits: this is a coverage-lapse or renewal-decision warning. It makes no
  claim about renewal probability, willingness to renew, obligation, or
  payment. `autoRenew` is stored but not consulted. A renewal decision date
  already in the past is not treated as a renewal window on its own; coverage
  can still fire the rule if the end date itself is inside the 30-day horizon.
  `PENDING` coverage is not reported as approaching expiry.
- Data-quality note: an `ACTIVE` entitlement whose end date has already passed
  is excluded by the live-coverage query and is therefore not reported by this
  rule. The record's status should be corrected by the entitlement lifecycle
  process rather than having health infer an expiry from the date.
- Proof: `RiskSignalControllerTest#approachingExpiryIncludesTheHorizonBoundaryAndNothingBeyondIt`,
  `#renewalDecisionDateInsideTheHorizonWarnsEvenWhenExpiryIsFarAway`,
  `#renewalDecisionDateBeyondTheHorizonRaisesNothing`,
  `#aPassedRenewalDecisionDateIsNotTreatedAsARenewalWindow`, and
  `#oneEntitlementNeverProducesTwoExpirySignals`.

#### ENTITLEMENT_EXPIRED (HIGH)

Fires when all of the following are true:

- the entitlement status is `EXPIRED`;
- `endDate` is non-null and strictly before today;
- no live entitlement replaces it.

A live entitlement is `ACTIVE` or `PENDING` with a non-null `endDate` at or
after today. It suppresses the expired signal when either:

- it is explicitly linked to the expired row through
  `renewedFromEntitlementId` / `renewedToEntitlementId`, or both rows have the
  same non-null `renewalDealId`; or
- it has the same non-null `accountId` and `offeringId` and its `startDate` is
  null or no later than `expired.endDate + 1 day`.

The second condition treats a null start date as unknown, not future-dated. A
future-dated `PENDING` renewal with a real coverage gap is therefore reported:
the gap is a fact the user should see.

- Event timestamp: `endDate` at 00:00 UTC.
- Evidence: status, start date, end date, renewal due date, auto-renew flag,
  renewable flag, and offering id.
- Exclusions: `CANCELLED` and `TERMINATED` coverage is treated as a deliberate
  business decision, not a lapse. `SUSPENDED` coverage is not considered a live
  replacement. An `ACTIVE` row past its end date does not suppress the signal
  because it is not live coverage. A renewal into a different offering is
  reported unless explicit linkage says it replaced the expired row.
- Limits: a renewal that has not been entered in the CRM cannot suppress the
  signal. Explicit linkage is also only as reliable as the data written to those
  columns; historical rows may rely on the account/offering continuity check.
  This is a fact about recorded coverage, not proof that the customer churned.
- Proof: `RiskSignalControllerTest#expiredEntitlementWithNoLaterCoverageIsReported`,
  `#renewalRecordedAsLaterCoverageSuppressesTheExpiredSignal`,
  `#continuousForwardDatedRenewalSuppressesTheExpiredSignal`,
  `#forwardDatedRenewalWithACoverageGapIsStillReported`,
  `#laterCoverageForADifferentOfferingDoesNotMaskTheLapse`,
  `#deliberatelyEndedCoverageIsNotReportedAsExpired`,
  `#expiredEndDateBoundaryIsStrictlyBeforeToday`, and
  `RiskSignalPersistenceIntegrationTest#expiredEntitlementIsShownForACoverageGapButNotForALinkedOrContinuousRenewal`.

### Deal rule

#### RECENT_DEAL_LOSS (MEDIUM)

Fires when a Deal belongs to the `CLOSED_LOST` record category and its loss
instant falls inside `[from, to)`.

The loss instant is:

- `closedDate` at 00:00 UTC when `closedDate` is present; otherwise
- `stageEnteredAt` when `closedDate` is absent.

`closedDate` wins when both exist because it is the recorded business close
date; `stageEnteredAt` is the fallback for legacy or inconsistent rows.

- Event timestamp: the loss instant above.
- Evidence: deal name, stage name, closed date, stage-entered time, and lost
  reason when present.
- Exclusions: deleted deals, other tenants, invisible deals, `CLOSED_WON` deals,
  rows outside the window, and rows with neither timestamp do not produce a
  signal. Every matching lost deal is evaluated; there is no 500-row cutoff.
- Limits: a lost deal is not automatically customer churn, contraction, or
  dissatisfaction; the CRM cannot distinguish a lost new logo from a lost
  upsell or renewal. A backdated close date is respected, so the signal can
  appear at a point in time different from when the row was created.
- Proof: `RiskSignalPersistenceIntegrationTest#dealLossWindowUsesTheActualLossDateNotCreationTime`.

### Cross-account listing behavior and limitations

- `type` and `severity` are pushed into PostgreSQL. An impossible pair (for
  example `LOW_CSAT` with `HIGH`) returns an empty page and zero total, not an
  error.
- Ordering, exact count, `LIMIT`, and `OFFSET` are applied to the full filtered
  signal set in PostgreSQL. The returned page is stable because the order ends
  with `sourceId` and signal type as tiebreakers.
- SQL returns only source coordinates; the service loads at most the current
  page's source rows and reuses the same mapping as the account endpoint.
- The account endpoint is still unpaginated and evaluates its sources in
  memory. It no longer truncates at 500 rows, so its response size grows with
  the number of matching signals.
- Account names are not part of the response. The UI shows a short account id;
  a null account id is shown as unavailable.
- Feedback signals have no detail route in the current UI and are therefore not
  linked. Ticket, entitlement, and deal signals link to their source records.
  No account or contact source route is invented.

### Policy status and deliberately absent rules

These rules are the implemented code contract, not a formally approved,
tenant-configurable customer-success policy. Changing a threshold or horizon is
a code change in `RiskSignalService`, not a settings change. The numbers mirror
the Feedback scales and the entitlement horizon currently in the code; they
should not be read as a customer-specific SLA or an approved risk appetite.

The following are deliberately absent because the schema does not define the
needed policy or data:

- no composite health score, letter grade, trend, or churn probability;
- no open-ticket count, repeat-ticket count, or "N tickets in M days" threshold;
- no renewal probability, renewal obligation, payment risk, or revenue-at-risk
  calculation;
- no product-usage, login, email, portal, or web-analytics engagement signal;
- no risk-score weighting, score suppression, or escalation across signal types.

A zero-signal account is not automatically healthy, and a multi-signal account
is not automatically lost. The signals are evidence for a human to review.

### Where the rules are verified

- `backend/src/test/java/com/shivang/crm/modules/feedback/RiskSignalControllerTest.java`
  pins the HTTP routes, `account:read` gate, parent-account visibility checks,
  explicit null `accountId` serialization, rule branches, severity ordering,
  source-scope isolation, and controller paging behavior.
- `backend/src/test/java/com/shivang/crm/modules/feedback/RiskSignalPersistenceIntegrationTest.java`
  runs the feedback threshold/window/source predicates, deal-loss
  timestamp/window rule, cross-account SQL count/filter/order/page path, account
  `OWN`/`TEAM` visibility, source-scope isolation, tenant isolation, null-account
  deal/entitlement handling, and entitlement gap/linkage suppression against the
  migrated PostgreSQL schema.
- `frontend/tests/customer-health-contract.check.cjs` checks the frontend type
  union, nullable-but-present `accountId`, labels, endpoint usage,
  pagination/filter contract, partial-permission notice, permission gate,
  source-link set, and the "empty is not healthy" copy against the backend
  contract.
- `frontend/tests/customer-health.spec.ts` covers the account Health tab and the
  global Customer Health page, including filters, pagination, empty/error states,
  source links, and partial-permission disclosure.

The controller tests use mocked repositories, so they prove rule branches and
the HTTP contract; the persistence test class is the proof that the feedback,
deal, cross-account SQL paging/filtering, owner scope, tenant isolation, and
entitlement-expiry suppression paths are actually evaluated by SQL. The
support-health and approaching-expiry rule branches still rely on
controller-level tests plus the shared predicate definitions.