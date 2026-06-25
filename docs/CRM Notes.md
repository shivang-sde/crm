Let’s define them **properly like a real CRM (similar to Salesforce)**—not just vague meanings. 

# **🧠 1\. Core Concept First (Very Important)**

CRM is built around this model:

👉 **Company (Account) → People (Contacts) → Business Opportunity (Deal)**  
 👉 **Owner \= Who is responsible for that record**

# **🏢 2\. ACCOUNT (Company / Organization)**

## **✅ Definition:**

An **Account** represents a **company or organization** you are doing business with.

## **📌 Examples:**

* Infosys  
* TCS  
* Google  
* A small local business

## **🧾 What it stores:**

* Company name  
* Industry  
* Website  
* Address  
* Revenue (optional)  
* Owner (salesperson responsible)

## **🔗 Relationships:**

* 1 Account → Many Contacts  
* 1 Account → Many Deals

---

## **🧠 Real Meaning:**

👉 “This is the company we are trying to sell to or already working with.”

## **👤 Owner (owner\_id) in Account:**

👉 The **salesperson responsible for managing that company relationship**

Example:

* Rahul handles “Infosys account”

So, 	account.owner\_id \= Rahul

# **👤 3\. CONTACT (Person)**

## **✅ Definition:**

A **Contact** is a **person working at an Account (company)**.

## **📌 Examples:**

* John (Manager at Google)  
* Priya (HR at TCS)

---

## **🧾 What it stores:**

* Name  
* Email  
* Phone  
* Job title  
* Linked Account

## **Relationships:**

* Many Contacts → 1 Account  
* Contact can be linked to Deals

## **🧠 Real Meaning:**

👉 “This is the actual human we talk to.”

## **👤 Owner (owner\_id) in Contact:**

👉 Person responsible for maintaining relationship with that contact

Example:

contact.owner\_id \= Sales Executive handling that person

# **💰 4\. DEAL (Opportunity)**

## **✅ Definition:**

A **Deal (Opportunity)** represents a **potential or ongoing sale**.

---

## **📌 Examples:**

* “Sell CRM to Infosys worth ₹10L”  
* “Annual subscription for TCS – ₹5L”

---

## **🧾 What it stores:**

* Deal name  
* Value (₹)  
* Stage (Negotiation, Won, Lost)  
* Probability  
* Close date  
* Linked Account \+ Contacts

---

## **🔗 Relationships:**

* Many Deals → 1 Account  
* Deal → linked Contacts (decision makers)

---

## **🧠 Real Meaning:**

👉 “This is the money we are trying to close.”

---

## **👤 Owner (owner\_id) in Deal:**

👉 Salesperson responsible for closing that deal

Example:

deal.owner\_id \= Sales Rep handling this opportunity

# **🧲 5\. LEAD (Pre-Sales Stage)**

## **✅ Definition:**

A **Lead** is an **unqualified potential customer**.

---

## **📌 Examples:**

* Someone filled a form  
* Imported data list  
* Cold call list

---

## **🧠 Real Meaning:**

👉 “We don’t yet know if this is a real customer.”

---

## **🔄 Lifecycle:**

Lead → Qualified → Convert →

  → Account (company)

  → Contact (person)

  → Deal (opportunity)

---

# **🔗 6\. RELATIONSHIP FLOW (MOST IMPORTANT)**

Lead (raw data)

  ↓ convert

Account (company)

  ↓

Contact (person)

  ↓

Deal (money opportunity)

# **👤 7\. OWNER (owner\_id) — CRITICAL CONCEPT**

## **✅ Definition:**

**Owner \= the user responsible for that record**

---

## **💡 Why it exists:**

* Accountability  
* RBAC (access control)  
* Reporting (who is performing)

🧾 Example: 

| Entity | Owner Meaning |
| ----- | ----- |
| Lead | Who is following up |
| Account | Who manages the company |
| Contact | Who handles communication |
| Deal | Who is closing the sale |

## **Used in RBAC:**

WHERE owner\_id \= current\_user

## **⚠️ Owner vs Assigned\_to (Important)**

You may also have:

* `owner_id` → responsible person  
* `assigned_to` → current handler (optional)

# **🧠 8\. REAL-WORLD EXAMPLE (END-TO-END)**

### **Step 1: Lead Created**

Name: Amit  
Company: ABC Pvt Ltd

### **Step 2: Converted**

Creates:

### **🏢 Account:**

ABC Pvt Ltd  
owner: Rahul

### **👤 Contact:**

Amit (Manager)

### **💰 Deal:**

CRM Implementation  
Value: ₹5L  
Stage: Negotiation  
owner: Rahul  
---

# **🔥 9\. Why This Structure Matters**

Because it enables:

✅ Pipeline tracking  
 ✅ Revenue forecasting  
 ✅ Relationship management  
 ✅ Multi-contact deals  
 ✅ Proper reporting

# **⚠️ Common Mistakes (Avoid These)**

❌ Treating Contact as Company  
 ❌ Skipping Account entity  
 ❌ No owner concept  
 ❌ Mixing Lead & Contact  
 ❌ No clear conversion flow

---

# **🧱 Final Mental Model**

Account \= Company  
Contact \= Person  
Deal \= Money Opportunity  
Lead \= Raw Prospect  
Owner \= Responsible User

# **🧠 1\. RBAC Layers (Your Mental Model)**

Access is NOT one thing. It’s 4 layers:

1\. Module Access   → Can user access Deals module?  
2\. Action Access   → Can user edit/delete/export?  
3\. Record Access   → Can user see THIS deal?  
4\. Field Access    → Can user see/edit THIS field?

👉 All 4 must pass → access granted

# **🧱 2\. Core RBAC Model (DB-Level)**

You already have base tables. Now we refine them.

---

## **🔹 roles**

id  
tenant\_id  
name  
parent\_role\_id   \-- hierarchy  
---

## **🔹 permissions**

id  
module         \-- lead, deal, contact  
action         \-- read, write, delete, assign, export  
---

## **🔹 role\_permissions**

role\_id  
permission\_id  
---

## **🔹 user\_roles**

user\_id  
role\_id

# **🔥 3\. Add ACCESS SCOPE (CRITICAL)**

This is where most systems fail.

---

## **🔹 role\_permissions (extended)**

role\_id  
permission\_id  
access\_scope

🧠 access\_scope values: 

| Scope | Meaning |
| ----- | ----- |
| ALL | Access all records |
| TEAM | Access team records |
| OWN | Only own records |
| NONE | No access  |

✅ Example: 

| Role | Module | Action | Scope |
| ----- | ----- | ----- | ----- |
| Sales Rep | Deal | READ | OWN |
| Manager | Deal | READ | TEAM |
| Admin | Deal | READ | ALL |

# **👥 4\. Team-Based Access**

You already have:

teams  
team\_members  
---

## **🔹 Add:**

record\_teams  
\- entity\_type (deal/contact)  
\- entity\_id  
\- team\_id  
---

👉 Why?

* Deals can be shared with teams  
* Multiple users can access

# **👤 5\. Record Ownership Model**

Every table has:

owner\_id  
---

## **🔐 Access Rules:**

### **OWN:**

WHERE owner\_id \= current\_user  
---

### **TEAM:**

WHERE owner\_id IN (team\_members)  
OR entity shared with user's team  
---

### **ALL:**

WHERE tenant\_id \= current\_tenant  
---

# **🧠 6\. Role Hierarchy (Manager Access)**

roles.parent\_role\_id  
---

## **Example:**

Admin  
 ↓  
Manager  
 ↓  
Sales Rep  
---

## **Rule:**

👉 Manager can access subordinates’ data

---

## **Query Logic:**

WHERE owner\_id IN (  
  SELECT user\_id FROM users  
  WHERE role IN (child\_roles\_of\_manager)  
)  
---

👉 You’ll need:

* Precomputed hierarchy (important for performance)

# **🔐 7\. Final Record Access Query (REAL LOGIC)**

For Deals:  
SELECT \* FROM deals d  
WHERE d.tenant\_id \= :tenant

AND (  
    \-- OWN  
    d.owner\_id \= :user\_id

    \-- TEAM  
    OR d.owner\_id IN (  
        SELECT tm.user\_id  
        FROM team\_members tm  
        WHERE tm.team\_id IN (:user\_teams)  
    )

    \-- SHARED RECORDS  
    OR EXISTS (  
        SELECT 1 FROM record\_teams rt  
        WHERE rt.entity\_id \= d.id  
        AND rt.team\_id IN (:user\_teams)  
    )  
    \-- ROLE HIERARCHY  
    OR d.owner\_id IN (:subordinate\_users)  
    \-- ALL ACCESS  
    OR :has\_all\_access \= true  
)

**⚙️ 8\. Field-Level Security**   
**🔹 field\_permissions**   
**id**  
**role\_id**  
**module**  
**field\_name**  
**can\_read**  
**can\_write**

| Role | Field | Read | Write |
| ----- | ----- | ----- | ----- |
| **Sales** | **deal.value** | **❌** | **❌** |
| **Manager** | **deal.value** | **✅** | **✅** |

## **Enforcement:**

### **Backend Filter:**

* **Remove fields from response**

### **Example:**

**{**  
 **"deal\_name": "ABC",**  
 **"value": null  // hidden**  
**}**

# **🔄 9\. Module-Level Access**

**Before any query:**

**Check:**  
**Does role have permission: DEAL\_READ ?**  
---

**If NO:**  
 **❌ Reject request**

# **⚡ 10\. Permission Evaluation Flow (IMPORTANT)**

**Request →**  
 **Authenticate (JWT) →**  
 **Get User \+ Roles →**  
 **Check Module Permission →**  
 **Apply Record Filter →**  
 **Apply Field Filter →**  
 **Return Data**

# **🧠 11\. Caching Strategy (CRITICAL)**

**RBAC is expensive if done naïvely.**

---

## **Cache:**

* **User roles**  
* **Permissions**  
* **Team membership**  
* **Role hierarchy**

**Use:**

* **Redis**

---

# **⚠️ 12\. Common Mistakes (Avoid These)**

---

**❌ Only role-based (no record-level access)**  
 **❌ No team sharing**  
 **❌ No hierarchy support**  
 **❌ Hardcoding permissions**  
 **❌ Checking access only in UI (very dangerous)**

# **🔥 13\. Advanced (Optional but Powerful)**

---

## **🔹 Attribute-Based Access (ABAC)**

**Example:**

**User can access deals WHERE region \= user.region**  
---

## **🔹 Data Masking**

* **Show partial values (e.g., phone/email)**

---

## **🔹 Temporary Access**

* **Grant access for limited time**

---

# **🧱 14\. Final RBAC Model Summary**

**User**  
**→ Roles**  
  **→ Permissions (module \+ action \+ scope)**  
    **→ Record Access (owner/team/hierarchy)**  
      **→ Field Access (visibility/editability)**  
---

# **🚀 What You Now Have**

**✅ Enterprise-grade RBAC**  
 **✅ Multi-tenant ready**  
 **✅ Scalable query model**  
 **✅ Extensible for future**

# **🧠 1\. What You Are Building (Clarified)**

**Your workflow engine \=**

**Event → Trigger → Conditions → Actions → Execution (async)**

**It must support:**

* **Real-time triggers**  
* **Async processing**  
* **Retry/failure handling**  
* **Extensibility (new actions later)**

---

# **🧱 2\. Core Components (Architecture)**

## **🔹 1\. Event Producer**

* **CRM modules publish events:**  
  * **LEAD\_CREATED**  
  * **DEAL\_UPDATED**  
  * **STAGE\_CHANGED**

---

## **🔹 2\. Event Queue**

**Use:**

* **RabbitMQ (start)**  
   **or**  
* **Apache Kafka (scale)**

---

## **🔹 3\. Workflow Processor (Consumer)**

* **Reads events**  
* **Matches workflows**  
* **Executes logic**

---

## **🔹 4\. Action Executors**

* **Email सेवा**  
* **SMS (via Twilio)**  
* **Assignment**  
* **Webhook caller**

---

## **🔹 5\. Execution Store**

* **Track runs, failures, retries**

---

# **🧩 3\. Workflow Definition Model (DB Design Refined)**

---

## **🔹 workflows**

**id**  
**tenant\_id**  
**name**  
**module**  
**is\_active**  
**created\_by**  
---

## **🔹 workflow\_triggers**

**id**  
**workflow\_id**  
**event\_type      \-- CREATE, UPDATE, DELETE**  
**entity          \-- lead, deal**  
---

## **🔹 workflow\_conditions**

**id**  
**workflow\_id**  
**field**  
**operator        \-- \=, \!=, \>, \<, IN**  
**value**  
**logical\_group   \-- AND / OR grouping**  
---

## **🔹 workflow\_actions**

**id**  
**workflow\_id**  
**action\_type     \-- EMAIL, SMS, ASSIGN, WEBHOOK**  
**execution\_order**  
**config\_json     \-- dynamic config**  
---

## **🔹 workflow\_executions**

**id**  
**workflow\_id**  
**entity\_id**  
**status          \-- SUCCESS, FAILED, RETRY**  
**attempts**  
**error\_message**  
**executed\_at**  
---

# **⚙️ 4\. Event Model (VERY IMPORTANT)**

**Define a standard event structure:**

**{**  
 **"eventType": "DEAL\_UPDATED",**  
 **"tenantId": "t1",**  
 **"entity": "deal",**  
 **"entityId": "d123",**  
 **"payload": {**  
   **"old": {...},**  
   **"new": {...}**  
 **},**  
 **"timestamp": "..."**  
**}**  
---

## **🔥 Why payload matters:**

* **Conditions evaluate on data**  
* **Actions use dynamic values**

---

# **🔄 5\. Execution Flow (Step-by-Step)**

---

## **🟢 Step 1: Event Published**

**Example:**

**Deal stage changed → publish DEAL\_UPDATED**  
---

## **🟢 Step 2: Workflow Processor Consumes**

**Fetch workflows WHERE:**  
**\- tenant\_id \= event.tenant\_id**  
**\- entity \= event.entity**  
**\- event\_type \= UPDATE**  
---

## **🟢 Step 3: Condition Evaluation**

**Example condition:**

**stage \== "Qualified"**

**Evaluate:**

**if (event.payload.new.stage.equals("Qualified"))**  
---

## **🟢 Step 4: Execute Actions**

**For each action:**

**1\. Assign user**  
**2\. Send email**  
**3\. Trigger webhook**  
---

## **🟢 Step 5: Store Execution Result**

**SUCCESS / FAILED / RETRY**  
---

# **🧠 6\. Condition Engine (Core Logic)**

---

## **🔹 Basic Conditions**

**field operator value**  
---

## **🔹 Advanced:**

**Support:**

* **AND / OR groups**  
* **Nested logic**

---

## **Example:**

**(stage \= "Qualified" AND value \> 50000\)**  
**OR (source \= "Website")**  
---

## **Implementation Approach:**

### **Option 1 (Simple):**

* **Evaluate in Java (recommended start)**

### **Option 2 (Advanced):**

* **Build expression parser**

---

# **⚡ 7\. Action Execution System**

---

## **🔹 Action Interface**

**interface WorkflowAction {**  
   **void execute(Event event, Map\<String, Object\> config);**  
**}**  
---

## **🔹 Implementations:**

### **1\. EmailAction**

* **Uses template**  
* **Sends email**

---

### **2\. SMSAction**

* **Uses Twilio**

---

### **3\. AssignAction**

**Update owner\_id**  
---

### **4\. WebhookAction**

**POST → external URL**  
---

## **🔥 Key Design:**

**👉 Each action \= independent module**  
 **👉 Easy to extend**

---

# **🔁 8\. Retry & Failure Handling (CRITICAL)**

---

## **Problem:**

* **Webhook fails**  
* **SMS fails**  
* **API timeout**

---

## **Solution:**

### **workflow\_executions:**

**attempts**  
**max\_attempts**  
**next\_retry\_at**  
---

## **Retry Strategy:**

* **Exponential backoff**

**Example:**

**Retry 1 → 1 min**   
**Retry 2 → 5 min**   
**Retry 3 → 15 min**   
---

## **Dead Letter Queue:**

* **Move failed events after max retries**

---

# **🧠 9\. Idempotency (Avoid Duplicate Execution)**

---

## **Problem:**

**Same event processed twice**

---

## **Solution:**

**Add:**

**event\_id (unique)**

**In execution table:**

**UNIQUE(event\_id, workflow\_id)**  
---

**👉 Prevent duplicate runs**

---

# **⚡ 10\. Async vs Sync Execution**

---

## **✅ Always use ASYNC**

**Why:**

* **Fast API response**  
* **Scalable**  
* **Fault tolerant**

---

## **Flow:**

**API → Save → Publish Event → Return response**  
---

# **🧠 11\. Delayed / Scheduled Workflows**

---

## **Example:**

**“Send email after 2 days”**  
---

## **Solution:**

* **Add delay queue**  
* **OR scheduled job**

---

## **Table:**

**scheduled\_actions**  
**\- execute\_at**  
**\- action\_config**  
---

# **🔐 12\. Security Considerations**

* **Validate webhook URLs**  
* **Rate limit actions**  
* **Prevent infinite loops**

---

## **Loop Example:**

**Workflow triggers itself repeatedly ❌**

**👉 Add:**

**execution\_depth\_limit**  
---

# **📊 13\. Observability (Very Important)**

---

## **Track:**

* **Execution time**  
* **Failures**  
* **Success rate**

---

## **Dashboard:**

* **“Workflow success rate”**  
* **“Top failing workflows”**

---

# **🧱 14\. Final Architecture Summary**

**CRM Action**  
  **↓**  
**Event Published**  
  **↓**  
**Message Queue**  
  **↓**  
**Workflow Processor**  
  **↓**  
**Condition Engine**  
  **↓**  
**Action Executors**  
  **↓**  
**Execution Store**  
---

# **🔥 15\. What Makes This Powerful**

**✅ Fully async**  
 **✅ Extensible actions**  
 **✅ Retry-safe**  
 **✅ Multi-tenant ready**  
 **✅ Scalable**

---

# **⚠️ Common Mistakes**

**❌ Running workflows synchronously**  
 **❌ Hardcoding logic**  
 **❌ No retry mechanism**  
 **❌ No idempotency**  
 **❌ No condition grouping**

**API Layer Structure**   
api/  
├── auth/  
├── tenant/  
├── users/  
├── leads/  
├── contacts/  
├── accounts/  
├── deals/  
├── activities/  
├── workflows/  
├── notifications/  
├── integrations/  
├── reports/  
└── common/

**🧱 3\. Standard API Design Principles**   
GET    /api/v1/leads  
POST   /api/v1/leads  
GET    /api/v1/leads/{id}  
PUT    /api/v1/leads/{id}  
DELETE /api/v1/leads/{id}

## **Stateless APIs**

Every request:

* JWT token  
* tenant context  
* permissions

# **IMPORTANT**

Frontend NEVER sends:

tenant\_id

Backend resolves it.

6\. Standard Response Format 

✅ Success Response 

{

  "success": true,

  "data": {...},

  "meta": {

    "page": 1,

    "size": 20,

    "total": 120

  }

}

❌ Error Response 

{

  "success": false,

  "error": {

    "code": "ACCESS\_DENIED",

    "message": "You do not have permission"

  }

}

