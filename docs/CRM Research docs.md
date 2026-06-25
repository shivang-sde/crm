

**Core CRM modules and data model:** Salesforce’s basics and object reference docs show the classic CRM primitives you’ll keep seeing across serious CRMs: accounts, contacts, leads, opportunities, tasks, notes, and standard objects; Microsoft Dynamics 365 Sales covers accounts/contacts, lead-to-order flow, marketing lists/campaigns, and service cases; HubSpot’s object docs show custom objects plus associations; Zoho’s feature matrix shows how real products bundle sales force automation, activities, forecasting, reporting, dashboards, and marketing automation.  
**Pipeline behavior and workflow rules:** HubSpot’s pipeline rules and automation docs are very practical for designing stages, approvals, required transitions, stage-based tasks/notifications, and the question of whether APIs/integrations should obey or bypass rules. That is a good model for how your own CRM should behave when sales or service records move through defined states.  
**RBAC and authorization:** NIST’s RBAC material gives you the formal foundation for roles, role hierarchies, and large-scale authorization; OWASP’s Authorization Cheat Sheet covers robust app-level authorization design; HubSpot’s permissions guide is useful because it shows how a commercial CRM splits access by object, team ownership, record ownership, export/import, associations, and other admin controls.  
**Multi-tenancy and tenant isolation:** Azure’s SaaS/multitenant architecture guidance explains the difference between SaaS and multitenancy and is useful for planning tenancy strategy from the start; AWS’s tenant isolation docs focus on limiting resource access by tenant context; AWS’s multitenant guidance and Azure tenancy-model guidance are especially helpful for understanding the trade-offs between isolation, cost, and complexity.  
**Integration surfaces and workflow orchestration:** Zapier’s trigger/webhook docs show the event-driven pattern you’ll need for incoming/outgoing data flows; Microsoft’s connector reference and Power Automate docs show how mature platforms expose hundreds of app connectors and workflow building blocks for cross-department automation.  
**CPaaS / calling / messaging layer:** Twilio’s Voice API and WhatsApp Business Calling docs are useful references for building CRM-triggered calling, call tracking, IVR, chat-to-voice handoffs, and consent-based outbound communication; Vonage’s Voice/SMS/Messages docs are another official CPaaS reference for programmable voice and messaging

 A good reading order is: **CRM objects and pipelines → permissions/RBAC → multitenancy/isolation → integrations/workflows → CPaaS/telephony**. A capability matrix mapped to MVP, growth, and enterprise tiers would be the most useful next artifact. 

# **Key Design Decisions (Don't Skip)**

## **1\. Multi-Tenancy Strategy**

Start with:

* tenant\_id in every table

## **2\. RBAC Model**

* Role → Permissions → Resource  
* Add hierarchy (Admin → Manager → Employee)

## **3\. Custom Fields System**

* JSONB or metadata tables  
* This is MUST for CRM flexibility

## **4\. Workflow Engine**

* Event-based triggers  
* Example:  
  * "Lead moved to stage X → send email"

# **🏗️ Recommended Tech Stack (Battle-Tested)**

## **🔹 1\. Backend (Core Engine)**

### **✅ Best Choice: Java \+ Spring Boot**

Since you're already working in Spring (based on your previous code), this is a strong advantage.

Use:

* Spring Boot  
* Spring Security  
* Hibernate

### **Why this works:**

* Enterprise-grade (same ecosystem as large CRMs)  
* Strong for RBAC, workflows, transactions  
* Easy microservices evolution later

👉 Alternative:

* Node.js (NestJS) → faster dev, but weaker for complex domain modeling  
* .NET → also excellent, but only if you're already in that ecosystem

## **Security Best Practices**

* Store access token in memory (NOT localStorage ideally)  
* Use HTTP-only cookies for refresh token  
* Add token rotation  
* Add logout invalidation (blacklist or versioning)

# **What About RBAC?**

With JWT:

* Don’t store full permissions inside token (bad idea)  
* Store:  
  * user\_id  
  * tenant\_id  
  * role\_id

Then fetch permissions server-side (cached)

## **Correct JWT Architecture**

### **Use 2 Tokens:**

1. **Access Token (short-lived)**  
   * 10–30 mins  
   * Used in API calls  
2. **Refresh Token (long-lived)**  
   * Stored securely (HTTP-only cookie)  
   * Used to get new access token

---

## **🔹 2\. Frontend (User Experience Layer)**

### **✅ Best Choice: React \+ Next.js**

Use:

* React \+ Next.js  
* Tailwind \+ shadcn UI  
* TanStack Query  
* Zustand

### **Why:**

* Component-driven → great for CRM modules  
* SSR/SEO (if needed)  
* Easy dashboard building

👉 Add:

* TanStack Query → data fetching  
* Zustand → lightweight state

---

## **🔹 3\. Database (Critical Decision)**

### **✅ Primary DB: PostgreSQL**

Use:

* PostgreSQL

### **Why:**

* Strong relational modeling (CRM \= relational-heavy)  
* JSONB for flexible schemas (custom fields)  
* Supports multi-tenancy patterns

👉 Pattern:

* Shared DB \+ tenant\_id (start)  
* Later → schema-per-tenant or DB-per-tenant

---

## **🔹 4\. Caching \+ Performance**

Use:

* Redis

For:

* Session caching  
* Rate limiting  
* Frequently accessed CRM data  
* Notification queues (lightweight)

---

## **🔹 5\. Messaging / Event System (VERY IMPORTANT)**

### **✅ Use:**

* Apache Kafka *(scale)*  
   OR  
* RabbitMQ *(simpler start)*

### **Why:**

Your CRM will need:

* Notifications  
* Activity logs  
* Integration triggers  
* Workflow execution

👉 Without event system → your system will break at scale

---

## **🔹 6\. Search (Often Ignored, But Critical)**

Use:

* Elasticsearch

Why:

* Fast global search across leads, contacts, notes  
* Filtering \+ autocomplete

---

## **🔹 7\. File Storage**

Use:

* Amazon S3

For:

* Attachments  
* Documents  
* CRM uploads

---

## **🔹 8\. Authentication & Identity**

Options:

### **Simple Start:**

* JWT \+ Spring Security

### **Scalable:**

* Keycloak

Why:

* Multi-tenant auth  
* SSO  
* OAuth2 for integrations

---

## **🔹 9\. DevOps & Deployment**

Use:

* Docker  
* Kubernetes *(later stage)*

Cloud:

* AWS / Azure / GCP (any, but pick one)

---

## **🔹 10\. Integration Layer (Your Differentiator)**

You MUST design this early.

Use:

* REST APIs  
* Webhooks  
* Event streaming

Optional:

* GraphQL layer

For CPaaS:

* Twilio  
* Vonage

# **🧩 FUNCTIONAL REQUIREMENTS (FR)**

## **🔹 1\. Organization & Multi-Tenancy**

### **Features:**

* Tenant (Organization) onboarding  
* Subdomain / tenant पहचान  
* Tenant-level configuration  
* Data isolation

👉 Example:

* `companyA.yourcrm.com`  
* `companyB.yourcrm.com`

---

## **🔹 2\. User & Access Management (RBAC)**

### **Core:**

* Users, Roles, Permissions  
* Role hierarchy (Admin → Manager → Employee)

### **Advanced (VERY IMPORTANT):**

* Record-level access:  
  * Owner-based  
  * Team-based  
* Field-level permissions  
* Module-level permissions

👉 This is where most CRMs fail.

---

## **🔹 3\. CRM Core Modules**

### **🧲 Leads**

* Capture (manual, API, import)  
* Assignment rules  
* Status lifecycle

### **👥 Contacts & Accounts**

* Company \+ individual mapping  
* Interaction history

### **💰 Deals / Opportunities**

* Pipeline stages  
* Value, probability  
* Stage-based rules

### **📅 Activities**

* Calls, meetings, tasks  
* Reminders & follow-ups

---

## **🔹 4\. Workflow Engine (Game Changer)**

### **Capabilities:**

* Trigger-based automation:  
  * On create/update/delete  
  * On stage change

### **Actions:**

* Send email / SMS  
* Assign user  
* Create task  
* Call webhook

👉 Example:

“When lead moves to ‘Qualified’ → assign to sales manager \+ send WhatsApp”

---

## **🔹 5\. Communication Layer (CPaaS Integration)**

* Call logging  
* SMS / WhatsApp integration  
* Email integration

👉 Integrate with:

* Twilio  
* Or similar providers

---

## **🔹 6\. Notifications System**

* In-app notifications  
* Email notifications  
* Real-time alerts

Configurable:

* Feature-based toggles (you already planned this 👍)

---

## **🔹 7\. Integration Framework**

### **Must Have:**

* REST APIs (public \+ internal)  
* Webhooks (incoming \+ outgoing)

### **Examples:**

* Lead created → send to external system  
* External system → create/update CRM record

---

## **🔹 8\. Customization Engine (Critical for SaaS)**

### **Features:**

* Custom fields (per module)  
* Custom pipelines  
* Custom statuses  
* Layout configuration

👉 Without this → your CRM is dead in market

---

## **🔹 9\. Reporting & Dashboard**

* Sales pipeline dashboard  
* Conversion rates  
* Activity reports  
* Custom reports

---

## **🔹 10\. Audit & Activity Tracking**

* Who changed what  
* Timeline view per record

---

## **🔹 11\. Document & File Management**

* Attachments per record  
* Document preview/download

**🧠 Step 1: Define Scope (Important Reality Check)** 

**“All-in-one CRM” doesn’t mean building everything at once.**

**👉 Break into platform \+ modules**

### **🧱 Platform Core (Must-have foundation)**

* **Multi-tenancy**  
* **RBAC**  
* **Workflow engine**  
* **Notification system**  
* **Integration framework**  
* **Audit & activity tracking**

### **📦 Business Modules (plug on top)**

* **Sales CRM**  
* **Marketing**  
* **Support (Tickets)**  
* **Tasks/Projects**  
* **Communication (CPaaS)**

# **🧩 FUNCTIONAL REQUIREMENTS (FR)**

## **🔹 1\. Organization & Multi-Tenancy**

### **Features:**

* **Tenant (Organization) onboarding**  
* **Subdomain / tenant पहचान**  
* **Tenant-level configuration**  
* **Data isolation**

**👉 Example:**

* **`companyA.yourcrm.com`**  
* **`companyB.yourcrm.com`**

## **`🔹 2. User & Access Management (RBAC)`**

### **`Core:`**

* **`Users, Roles, Permissions`**  
* **`Role hierarchy (Admin → Manager → Employee)`**

### **`Advanced (VERY IMPORTANT):`**

* **`Record-level access:`**  
  * **`Owner-based`**  
  * **`Team-based`**  
* **`Field-level permissions`**  
* **`Module-level permissions`**

**`👉 This is where most CRMs fail.`**

## **`🔹 3. CRM Core Modules`**

### **`🧲 Leads`**

* `Capture (manual, API, import)`  
* `Assignment rules`  
* `Status lifecycle`

### **`👥 Contacts & Accounts`**

* `Company + individual mapping`  
* `Interaction history`

### **`💰 Deals / Opportunities`**

* `Pipeline stages`  
* `Value, probability`  
* `Stage-based rules`

### **`📅 Activities`**

* `Calls, meetings, tasks`  
* `Reminders & follow-ups`

## **`🔹 4. Workflow Engine (Game Changer)`**

### **`Capabilities:`**

* `Trigger-based automation:`  
  * `On create/update/delete`  
  * `On stage change`

### **`Actions:`**

* `Send email / SMS`  
* `Assign user`  
* `Create task`  
* `Call webhook`

`👉 Example:`

`“When lead moves to ‘Qualified’ → assign to sales manager + send WhatsApp”`

## **`🔹 5. Communication Layer (CPaaS Integration)`**

* `Call logging`  
* `SMS / WhatsApp integration`  
* `Email integration`

`👉 Integrate with:`

* `Twilio`  
* `Or similar providers`

---

## **`🔹 6. Notifications System`**

* `In-app notifications`  
* `Email notifications`  
* `Real-time alerts`

`Configurable:`

* `Feature-based toggles (you already planned this 👍)`

## **`🔹 7. Integration Framework`**

### **`Must Have:`**

* `REST APIs (public + internal)`  
* `Webhooks (incoming + outgoing)`

### **`Examples:`**

* `Lead created → send to external system`  
* `External system → create/update CRM record`

## **`🔹 8. Customization Engine (Critical for SaaS)`**

### **`Features:`**

* `Custom fields (per module)`  
* `Custom pipelines`  
* `Custom statuses`  
* `Layout configuration`

`👉 Without this → your CRM is dead in market`

---

## **`🔹 9. Reporting & Dashboard`**

* `Sales pipeline dashboard`  
* `Conversion rates`  
* `Activity reports`  
* `Custom reports`

## **`🔹 10. Audit & Activity Tracking`**

* `Who changed what`  
* `Timeline view per record`

---

## **`🔹 11. Document & File Management`**

* `Attachments per record`  
* `Document preview/download`

# **`⚙️ NON-FUNCTIONAL REQUIREMENTS (NFR)`**

`This is where real SaaS systems are built.`

---

## **`🔹 1. Scalability`**

* `Horizontal scaling (stateless APIs)`  
* `Support 1000s of tenants`  
* `Async processing (queue-based)`

## **`🔹 2. Performance`**

* `API response < 300ms (normal ops)`  
* `Pagination everywhere`  
* `Caching (Redis)`

---

## **`🔹 3. Security`**

* `JWT-based auth`  
* `Data isolation per tenant`  
* `Encryption (at rest + transit)`  
* `Role-based + record-level authorization`

---

## **`🔹 4. Availability`**

* `99.9% uptime target`  
* `Fault-tolerant services`

## **`🔹 5. Multi-Tenancy Strategy`**

`Start with:`

* `Shared DB + tenant_id`

`Later:`

* `Schema-per-tenant (optional)`

---

## **`🔹 6. Observability`**

* `Logging`  
* `Monitoring`  
* `Audit trails`

---

## **`🔹 7. Extensibility`**

* `Plugin/module-based architecture`  
* `API-first design`

---

## **`🔹 8. Data Consistency`**

* `Strong consistency for CRM core`  
* `Eventual consistency for async workflows`

---

## **`🔹 9. Compliance (Future)`**

* `GDPR-like controls`  
* `Data export/delete`

# **`HOW THIS FEEDS INTO HLD`**

`Your HLD will map like this:`

### **`Core Services:`**

* `Auth Service`  
* `Tenant Service`  
* `CRM Core Service`  
* `Workflow Service`  
* `Notification Service`  
* `Integration Service`

# **`🚀 MVP vs Phase Plan (Very Important)`**

## **`✅ MVP (Don’t skip discipline)`**

* `Multi-tenancy`  
* `RBAC (basic)`  
* `Leads + Contacts + Deals`  
* `Activities`  
* `Basic workflow (1–2 triggers)`  
* `REST APIs`

## **`🔄 Phase 2`**

* **`Notifications`**  
* **`Integrations (webhooks)`**  
* **`Custom fields`**  
* **`Reports`**

---

## **`🔥 Phase 3`**

* **`CPaaS (calls, WhatsApp)`**  
* **`Advanced workflow engine`**  
* **`AI features`**

## **`Core Domains`**

### **`🔹 A. Identity & Tenant Domain`**

* **`Organization (Tenant)`**  
* **`Users`**  
* **`Roles`**  
* **`Permissions`**  
* **`Teams`**

---

### **`🔹 B. CRM Domain`**

* **`Leads`**  
* **`Contacts`**  
* **`Accounts`**  
* **`Deals (Opportunities)`**  
* **`Activities`**

---

### **`🔹 C. Automation Domain`**

* **`Workflows`**  
* **`Rules`**  
* **`Triggers`**  
* **`Actions`**

---

### **`🔹 D. Communication Domain`**

* **`Calls`**  
* **`SMS / WhatsApp`**  
* **`Email`**

**`(Integration with Twilio or similar)`**

---

### **`🔹 E. Integration Domain`**

* **`APIs`**  
* **`Webhooks`**  
* **`External sync`**

---

### **`🔹 F. Customization Domain`**

* **`Custom fields`**  
* **`Layouts`**  
* **`Pipelines`**

---

### **`🔹 G. Observability Domain`**

* **`Audit logs`**  
* **`Activity timeline`**

---

### **`🔹 H. Analytics Domain`**

* **`Reports`**  
* **`Dashboards`**

---

**`👉 This separation is critical for your HLD and future microservices.`**

# **`2. Refined Functional Requirements (SYSTEM-READY)`**

**`Now I’ll sharpen your requirements with real system-level clarity.`**

---

## **`🔹 1. Organization & Multi-Tenancy`**

### **`Functional Expectations:`**

* **`Tenant creation (self-serve + admin)`**  
* **`Subdomain-based routing`**  
  * **`tenant_slug.yourcrm.com`**  
* **`Tenant context resolution per request`**  
* **`Tenant-specific:`**  
  * **`settings`**  
  * **`branding`**  
  * **`modules enabled/disabled`**

### **`Edge Cases:`**

* **`Same email across tenants ✅ allowed`**  
* **`Cross-tenant data leakage ❌ never`**

---

## **`🔹 2. RBAC (Make This Your Strongest Feature)`**

### **`Minimum Model:`**

* **`User → Role → Permissions`**

### **`Advanced Model (YOU MUST BUILD):`**

* **`Role hierarchy`**  
* **`Permission types:`**  
  * **`Read / Write / Delete / Assign / Export`**

### **`Access Levels:`**

* **`Organization-wide`**  
* **`Team-level`**  
* **`Owner-only`**

### **`Field-Level Security:`**

* **`Example:`**  
  * **`Salary visible only to HR`**  
  * **`Deal value hidden from junior sales`**

---

## **`🔹 3. CRM Core Modules (Refined)`**

### **`🧲 Leads`**

* **`Source tracking (API, form, import)`**  
* **`Deduplication logic`**  
* **`Assignment:`**  
  * **`Manual`**  
  * **`Round-robin`**  
  * **`Rule-based`**

---

### **`👥 Contacts & Accounts`**

* **`Contact ↔ Account relationship`**  
* **`Multiple contacts per account`**  
* **`Activity timeline aggregation`**

---

### **`💰 Deals / Opportunities`**

* **`Pipeline-based structure:`**  
  * **`Multiple pipelines per tenant`**  
* **`Stage:`**  
  * **`name`**  
  * **`probability`**  
  * **`order`**  
* **`Stage transition rules:`**  
  * **`required fields`**  
  * **`validations`**

---

### **`📅 Activities`**

* **`Types:`**  
  * **`Call`**  
  * **`Meeting`**  
  * **`Task`**  
* **`Linked to:`**  
  * **`Lead / Contact / Deal`**

---

## **`🔹 4. Workflow Engine (Define Clearly)`**

**`This is not just “automation”—it’s a mini rule engine.`**

### **`Triggers:`**

* **`On Create`**  
* **`On Update`**  
* **`On Delete`**  
* **`On Field Change`**  
* **`On Schedule (cron-based)`**

---

### **`Conditions:`**

* **`Field-based logic:`**  
  * **`deal.stage == "Qualified"`**  
  * **`lead.source == "Website"`**

---

### **`Actions:`**

* **`Assign user`**  
* **`Send email`**  
* **`Send SMS (via Twilio)`**  
* **`Create record`**  
* **`Trigger webhook`**

---

### **`Key Requirement:`**

**`👉 Must be configurable via UI (not hardcoded)`**

---

## **`🔹 5. Communication Layer`**

### **`Features:`**

* **`Call logging (manual + API-based)`**  
* **`SMS / WhatsApp send + history`**  
* **`Email sync (future: IMAP/SMTP)`**

---

## **`🔹 6. Notification System`**

### **`Types:`**

* **`In-app`**  
* **`Email`**  
* **`Push (future)`**

### **`Config:`**

* **`Feature-level toggle`**  
* **`User-level preference`**

---

## **`🔹 7. Integration Framework`**

### **`APIs:`**

* **`REST APIs for all modules`**

### **`Webhooks:`**

* **`Outgoing:`**  
  * **`Event → external system`**  
* **`Incoming:`**  
  * **`External → CRM action`**

---

## **`🔹 8. Customization Engine (Very Critical)`**

### **`Must Support:`**

* **`Custom fields:`**  
  * **`text, number, date, dropdown`**  
* **`Per module`**  
* **`Per tenant`**

---

### **`Advanced:`**

* **`Dynamic forms`**  
* **`Layout builder`**  
* **`Pipeline customization`**

---

## **`🔹 9. Reporting`**

### **`Basic:`**

* **`Predefined dashboards`**

### **`Advanced:`**

* **`Custom report builder`**  
* **`Filters + grouping`**

---

## **`🔹 10. Audit & Timeline`**

### **`Audit:`**

* **`Field-level changes`**  
* **`Who + when`**

### **`Timeline:`**

* **`Unified view:`**  
  * **`calls`**  
  * **`emails`**  
  * **`updates`**

---

## **`🔹 11. Document Management`**

* **`File upload (per record)`**  
* **`Storage abstraction (e.g., S3)`**  
* **`Access control`**

# **`3. Missing (You Should Add These)`**

**`These are things people forget—but matter a lot.`**

---

## **`🔥 A. Data Import/Export`**

* **`CSV import (leads, contacts)`**  
* **`Bulk update`**  
* **`Export controls (RBAC-based)`**

---

## **`🔥 B. Duplicate Management`**

* **`Detect duplicate leads/contacts`**  
* **`Merge records`**

---

## **`🔥 C. Soft Delete & Recovery`**

* **`Trash system`**  
* **`Restore option`**

---

## **`🔥 D. Tagging System`**

* **`Labels for records (flexible filtering)`**

---

## **`🔥 E. Global Search`**

* **`Across all modules`**

# **`🧱 4. Final Structured FR (Ready for HLD)`**

**`You now have:`**

### **`Core Platform:`**

* **`Multi-tenancy`**  
* **`RBAC`**  
* **`Customization engine`**  
* **`Workflow engine`**  
* **`Integration system`**

### **`Business Layer:`**

* **`Leads`**  
* **`Contacts`**  
* **`Deals`**  
* **`Activities`**

### **`Support Layer:`**

* **`Notifications`**  
* **`Communication`**  
* **`Reporting`**  
* **`Audit`**

## **`🔹 1. Auth & RBAC Service`**

### **`Responsibilities:`**

* `JWT authentication`  
* `Role & permission management`  
* `Access checks`

### **`Key Tables:`**

`users`

`roles`

`permissions`

`role_permissions`

`user_roles`

`teams`

---

## **`🔹 2. Tenant Service`**

### **`Responsibilities:`**

* `Tenant onboarding`  
* `Subdomain mapping`  
* `Tenant config`

### **`Tables:`**

`tenants`

`tenant_settings`

`tenant_domains`

## **`🔹 3. CRM Core Service`**

### **`Modules:`**

* `Leads`  
* `Contacts`  
* `Accounts`  
* `Deals`  
* `Activities`

### **`Tables:`**

`leads`

`contacts`

`accounts`

`deals`

`activities`

`👉 Each table MUST have:`

`tenant_id`

`created_by`

`owner_id`

`created_at`

`updated_at`

---

## **`🔹 4. Workflow Service`**

### **`Responsibilities:`**

* `Store workflow definitions`  
* `Execute triggers`  
* `Fire actions`

### **`Tables:`**

`workflows`

`workflow_triggers`

`workflow_conditions`

`workflow_actions`

`workflow_executions`

---

## **`🔹 5. Notification Service`**

### **`Responsibilities:`**

* `Send notifications`  
* `Manage templates`  
* `User preferences`

### **`Tables:`**

`notifications`

`notification_templates`

`user_notification_settings`

---

## **`🔹 6. Integration Service`**

### **`Responsibilities:`**

* `Webhooks`  
* `External API sync`

### **`Tables:`**

`webhooks`

`webhook_logs`

`api_tokens`

`integration_configs`

---

## **`🔹 7. Customization Service`**

### **`Responsibilities:`**

* `Custom fields`  
* `Layout configs`

### **`Tables:`**

`custom_fields`

`custom_field_values`

`layouts`

`pipelines`

`pipeline_stages`

---

## **`🔹 8. Audit Service`**

### **`Responsibilities:`**

* `Track changes`

### **`Tables:`**

`audit_logs`

`activity_logs`

# **`🧩 4. Multi-Tenancy Design`**

## **`✅ Approach: Shared DB + tenant_id`**

`Every table:`

`tenant_id (MANDATORY)`

---

## **`🔐 Request Flow:`**

1. `Request hits API`  
2. `Extract tenant from:`  
   * `subdomain OR JWT`  
3. `Attach tenant_id to context`  
4. `All queries filtered by tenant_id`

---

## **`⚠️ Rule:`**

`👉 NEVER trust frontend for tenant_id`  
 `👉 Always resolve server-side`

---

# **`🔐 5. RBAC + Data Access Model`**

## **`🔹 Access Layers:`**

### **`1. Module Level`**

* `Can user access "Deals"?`

### **`2. Record Level`**

* `Owner`  
* `Team`  
* `Org-wide`

### **`3. Field Level`**

* `Hide sensitive fields`

## **`Example Query Enforcement:`**

`SELECT * FROM deals`

`WHERE tenant_id = ?`

`AND (`

   `owner_id = ?`

   `OR team_id IN (user_teams)`

   `OR access_level = 'ORG'`

`)`

# **`6. Event-Driven Flow (VERY IMPORTANT)`**

`Use:`

* `RabbitMQ (start)`  
   `or`  
* `Apache Kafka (scale)`

---

## **`Example Flow:`**

### **`Lead Created:`**

1. `Lead saved in DB`  
2. `Event published:`

`LEAD_CREATED`

3. `Consumers:`  
* `Workflow Service → evaluate rules`  
* `Notification Service → send alerts`  
* `Integration Service → fire webhook`

---

`👉 This decouples everything`

# **`7. API Design Pattern`**

## **`Use REST (initially)`**

### **`Example:`**

`POST /api/leads`

`GET /api/deals`

`PUT /api/contacts/{id}`

---

## **`Standards:`**

* `Pagination`  
* `Filtering`  
* `Sorting`

---

# **`🧠 8. Custom Fields Design (Critical)`**

## **`Approach:`**

### **`Metadata Table:`**

`custom_fields`

`- id`

`- module (lead/contact/deal)`

`- field_name`

`- type`

`- tenant_id`

---

### **`Values Table:`**

`custom_field_values`

`- entity_id`

`- field_id`

`- value`

---

`👉 OR use PostgreSQL JSONB (hybrid approach)`

---

# **`📊 9. Workflow Execution Flow`**

`Event → Trigger Match → Condition Check → Action Execution`

`Example:`

`Deal Updated → Stage Changed → If Stage=Closed Won → Send Email`

---

# **`🚀 10. Scaling Strategy`**

## **`Phase 1:`**

* `Monolith`  
* `PostgreSQL`  
* `Redis`  
* `RabbitMQ`

## **`Phase 2:`**

* `Extract:`  
  * `Workflow Service`  
  * `Notification Service`

## **`Phase 3:`**

* `Full microservices`  
* `Kafka`  
* `Elasticsearch`

# **`🔥 11. Critical Design Rules`**

### **`✅ MUST DO:`**

* `Tenant isolation everywhere`  
* `RBAC at DB + service layer`  
* `Async processing for workflows`  
* `Audit everything`

---

### **`❌ NEVER DO:`**

* `Hardcode workflows`  
* `Skip event system`  
* `Ignore indexing (DB performance killer)`  
* `Store business logic in controllers`

`6. ER Diagram (Conceptual View)` 

`Tenant`

 `├── Users ── Roles ── Permissions`

 `│     └── Teams`

 `│`

 `├── Leads`

 `├── Accounts ── Contacts`

 `│        └── Deals ── Pipeline Stages`

 `│`

 `├── Activities (linked to all)`

 `│`

 `├── Custom Fields → Values`

 `│`

 `├── Workflows → Triggers → Actions`

 `│`

 `├── Notifications`

 `│`

 `├── Webhooks / Integrations`

 `│`

 `└── Audit Logs / Files`

