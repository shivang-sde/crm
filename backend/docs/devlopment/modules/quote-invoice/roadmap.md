he quotation/invoicing integration application should be treated as a future integration target, not as a prerequisite for CRM completeness.

So the CRM itself should remain capable of supporting the full commercial journey independently:

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

The external MySQL + Node.js application can later become an optional connected system:

                 ┌──────────────────────────┐
                 │       CRM Platform        │
                 │                          │
Lead → Account → Contact → Deal → Quote ───┼────┐
                 │                          │    │
                 └──────────────────────────┘    │
                                                 │ Integration
                 ┌──────────────────────────┐    │
                 │ Existing Quotation/      │◀───┘
                 │ Invoice Application      │
                 │ MySQL + Node.js          │
                 └──────────────────────────┘
Therefore I would split the roadmap like this

Now — CRM-native capability

Customer / Account Expansion
Account 360
Contacts and relationships
Multiple deals per account
Account revenue/pipeline
Customer lifecycle
Account activities
Account health / engagement
Expansion opportunities
Cross-sell / upsell opportunities
Renewal visibility
CRM-native Quoting / Commercial Execution
Quote entity
Quote creation from Deal
Quote line items
Pricing/discount/tax
Quote versions/revisions
Quote status lifecycle
Quote → Deal relationship
Quote totals
Approval where actually required
Quote acceptance/rejection
Commercial events/workflow integration
Eventually Order/Invoice capability if that belongs in the CRM's independent product boundary
Later — Integration capability

The existing Node.js/MySQL quotation/invoice application becomes an external commercial system connector.

That integration should be designed so that:

CRM can work without the external application.
External application can work with CRM.
Either system can be the source of truth for explicitly agreed objects.
We don't tightly couple CRM domain entities to the external application's MySQL schema.
Integration is provider/application-neutral.
Existing CRM Workflow Engine and canonical events can be reused.
We don't prematurely copy their database design into CRM.
One important architectural distinction

I would not start by implementing Quote based on the other application's schema.

First establish:

What does a Quote mean in our CRM domain?

Then later map:

CRM Quote
   ↕
Integration Mapping
   ↕
External Quotation Application

rather than:

External MySQL schema
        ↓
copy schema into CRM