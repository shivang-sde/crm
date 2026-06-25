The biggest mistake CRM teams make is creating custom fields only for Leads. After six months they realize Accounts, Contacts, Deals, Tickets, Vendors, Assets, Projects all need custom fields.

Instead, build a generic custom field engine once.

Something like: custom_fields

id
tenant_id
module
name
label
field_type
required
options_json
created_at
updated_at

where module is:
LEAD
CONTACT
ACCOUNT
DEAL

and

custom_field_values
id
record_id
field_id
value

Then the same engine powers the entire CRM.

That is much closer to how Salesforce, Zoho CRM, Dynamics CRM, and HubSpot are architected internally, and it prevents a major rewrite when customers start asking for "just one more field."