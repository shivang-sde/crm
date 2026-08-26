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