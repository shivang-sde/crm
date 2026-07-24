# SellSpark Manual Test Guide

This guide provides instructions and payloads to manually verify the SellSpark integration end-to-end using PowerShell.

## Prerequisites
- Backend running on `http://localhost:8080`
- Frontend running on `http://localhost:3000`
- A valid tenant slug (e.g. `test-tenant`) and provider key (`sellspark_voice`).
- You need the CRM Call ID (UUID) that gets created after clicking "Click to Call" in the UI.

## 1. Click-to-Call
1. Open a Lead or Contact in the CRM UI.
2. Click the phone number to trigger a call.
3. Observe the CRM Call is created in "PLANNED" status.
4. **Copy the CRM Call ID** from the URL (e.g., `/calls/active/123e4567-e89b-12d3-a456-426614174000`). This is your `$correlationKey`.

## 2. Simulate Connect Webhook
This webhook notifies the CRM that the call has connected. Replace the variables in the PowerShell script below.

```powershell
$tenantSlug = "default-tenant"
$providerKey = "sellspark_voice"
$correlationKey = "<PASTE_CRM_CALL_ID_HERE>"
$externalCallId = "SELLSPARK-" + (Get-Date -UFormat %s)
$currentTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

$body = @"
{
  "call_uniqueid": "$externalCallId",
  "lead_id": "$correlationKey",
  "agent": "agent123",
  "agent_number": "1001",
  "agent_name": "John Doe",
  "call_with": "9876543210",
  "did_no": "1122334455",
  "call_type": "outbound",
  "start_time": "$currentTime",
  "meta_data": "campaign_1"
}
"@

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/connectors/$tenantSlug/$providerKey/call-connect" -Method Post -Body $body -ContentType "application/json"
```

**Expected Result:**
- The CRM Call status is still PLANNED (it becomes HELD on CDR).
- The Active Call page updates live (because of 4-second polling) to show the start time.
- If you were on another page, a call opening modal/sidebar should trigger (because of the `useCallOpeningEvents` polling).

## 3. Simulate CDR Webhook
This webhook notifies the CRM that the call has finished.

```powershell
$tenantSlug = "default-tenant"
$providerKey = "sellspark_voice"
$correlationKey = "<PASTE_CRM_CALL_ID_HERE>"
$externalCallId = "<PASTE_EXTERNAL_CALL_ID_FROM_STEP_2>"
$startTime = (Get-Date).AddMinutes(-2).ToString("yyyy-MM-dd HH:mm:ss")
$endTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

$body = @"
{
  "call_id": "$externalCallId",
  "uniqueid": "EVENT-CDR-12345",
  "lead_id": "$correlationKey",
  "agent": "agent123",
  "agent_no": "1001",
  "applicant_no": "9876543210",
  "call_type": "outbound",
  "start_time": "$startTime",
  "end_time": "$endTime",
  "call_duration": "125",
  "rec_path": "https://sellspark.com/recordings/$externalCallId.mp3",
  "resource_url": "https://sellspark.com/res/$externalCallId",
  "status": "answered"
}
"@

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/connectors/$tenantSlug/$providerKey/cdr" -Method Post -Body $body -ContentType "application/json"
```

**Expected Result:**
- The CRM Call status changes to "HELD".
- The duration displays as `2m 5s`.
- The "Listen" link for the recording appears.
- The Call Disposition form is now visible.

## 4. Inbound Call (No prior click-to-call)
Simulate a connect webhook without a matching `$correlationKey`.

```powershell
$tenantSlug = "default-tenant"
$providerKey = "sellspark_voice"
$externalCallId = "INBOUND-" + (Get-Date -UFormat %s)
$currentTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

$body = @"
{
  "call_uniqueid": "$externalCallId",
  "lead_id": "",
  "agent": "agent123",
  "agent_number": "1001",
  "agent_name": "John Doe",
  "call_with": "9998887776",
  "did_no": "1122334455",
  "call_type": "inbound",
  "start_time": "$currentTime",
  "meta_data": ""
}
"@

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/webhooks/connectors/$tenantSlug/$providerKey/call-connect" -Method Post -Body $body -ContentType "application/json"
```

**Expected Result:**
- A new CRM Call is created automatically.
- A call opening event is broadcasted.
- Your frontend should pick it up and show the incoming call UI (if you're logged into the tenant).
