## Requiment Chnages
- click to call api's integration (any 3rd party self configurable via differnt store object such as lead, user, deal, account, contact, or other so that we can get respected or related variable or filed)
similar approach will be used in workflow also.

- webrtc emebed code, like if users want to used webrtc based calling rather click to call will be using webract and ask users credential username or password this also must be independent of provider

note: any customization will be doing will be independent of providor and integration 


Outgoing Webhooks (CRM → External System)
These are the most common:

Record Events → Trigger when a lead, contact, deal, or task is created, updated, or deleted.

Workflow/Automation Events → Fire when a stage changes, assignment happens, or a workflow rule executes.

Communication Events → Notify when an email is sent/opened, a call is logged, or a chat message arrives.

Custom Module Events → Any custom object (like “Support Ticket”) can send create/update/delete notifications.

👉 Outgoing webhooks are used to push CRM data to external apps (ERP, marketing tools, Slack, etc.).

📥 Incoming Webhooks (External System → CRM)
These are less common but powerful:

Data Insertion → External apps send new leads, contacts, or deals into the CRM.

Updates → External systems update CRM records (e.g., payment status from a billing app).

Activity Logging → External events (like website form submissions or chatbot conversations) are logged as CRM activities.

Custom Actions → External triggers can start workflows inside the CRM (e.g., when a customer subscribes, CRM creates a task).

👉 Incoming webhooks are used to pull external events/data into the CRM.

⚡ 80/20 Rule Takeaway
Outgoing (push): Focus on create/update/delete + stage change → covers most integrations.

Incoming (pull): Focus on new lead creation + record updates → covers most external data syncs.