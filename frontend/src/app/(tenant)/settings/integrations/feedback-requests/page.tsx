"use client";

import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { KeyRound, Link2, LockKeyhole } from "lucide-react";

function Code({ children }: { children: string }) {
  return (
    <pre className="overflow-x-auto rounded-md border bg-muted p-3 text-xs">
      <code>{children}</code>
    </pre>
  );
}

export default function FeedbackRequestSettingsPage() {
  return (
    <SettingsLayout>
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
            <Link2 className="h-6 w-6 text-indigo-600" />
            Feedback Requests
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Ask a customer to rate one support ticket through a single-use link, or ingest
            trusted feedback from your commercial integration.
          </p>
        </div>

        <Alert>
          <LockKeyhole className="h-4 w-4" />
          <AlertDescription>
            Public links expire after 30 days and can be submitted once. The raw token is shown
            only when a link is created or regenerated; the CRM stores only its SHA-256 hash and
            never places it in lists or logs.
          </AlertDescription>
        </Alert>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Generated API reference</CardTitle>
            <CardDescription>
              The live SpringDoc output includes these endpoints alongside the existing feedback API.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-4 text-sm">
            <a
              href="/swagger-ui/index.html"
              target="_blank"
              rel="noreferrer"
              className="font-medium text-primary underline underline-offset-4"
            >
              Open Swagger UI
            </a>
            <a
              href="/v3/api-docs"
              target="_blank"
              rel="noreferrer"
              className="font-medium text-primary underline underline-offset-4"
            >
              Open OpenAPI JSON
            </a>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Existing authenticated feedback API</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Code>{`GET  /api/v1/feedback   (feedback:read)
POST /api/v1/feedback   (feedback:write)`}</Code>
            <p className="text-muted-foreground">
              The authenticated API resolves the customer by email and accepts the same CSAT, NPS
              and CES validation. A staff caller cannot claim a customer-originated source; without
              integration authority the source is normalized to INTERNAL. WEBSITE and API sources
              are customer-originated and remain eligible for the existing Customer Health rules.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Sending a ticket feedback link</CardTitle>
            <CardDescription>
              Open a ticket and use the Feedback tab. Generating a link requires ticket:write and
              feedback:write.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p>
              The ticket supplies the customer and contact. The request supports CSAT (1-5), NPS
              (0-10) and CES (1-7); CSAT is the default. A ticket can have one pending request at
              a time. Regenerating revokes the old link and returns a new one.
            </p>
            <p className="text-muted-foreground">
              Staff endpoints are nested under the ticket and also enforce record scope:
            </p>
            <Code>{`POST /api/v1/tickets/{ticketId}/feedback-requests
GET  /api/v1/tickets/{ticketId}/feedback-requests
POST /api/v1/tickets/{ticketId}/feedback-requests/{requestId}/revoke
POST /api/v1/tickets/{ticketId}/feedback-requests/{requestId}/regenerate`}</Code>
            <Code>{`POST /api/v1/tickets/{ticketId}/feedback-requests
Content-Type: application/json

{ "type": "CSAT" }`}</Code>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Public response endpoints</CardTitle>
            <CardDescription>
              No authentication. The token is the credential and only safe request metadata is
              exposed.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <Code>{`GET  /api/v1/public/feedback/{token}
POST /api/v1/public/feedback/{token}

{ "score": 5, "comment": "Quick and helpful" }`}</Code>
            <p className="text-muted-foreground">
              Responses: 404 for an unknown token, 409 when already submitted, 410 when expired or
              revoked. A submitted response is immutable and is recorded against the
              ticket&apos;s customer with source WEBSITE.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <KeyRound className="h-4 w-4" />
              Trusted integration API
            </CardTitle>
            <CardDescription>
              Uses the same tenant commercial API key as Commercial Integration. No separate
              credential is issued.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <Code>{`POST /api/v1/integrations/commercial/feedback
X-API-Key: <tenant commercial API key>
Content-Type: application/json

{
  "email": "customer@example.com",
  "type": "CSAT",
  "score": 4,
  "comment": "Resolved on first contact",
  "externalReference": "ticket-4821-response"
}`}</Code>
            <p className="text-muted-foreground">
              The customer is resolved by email inside the key&apos;s tenant. Source defaults to
              API. Supplying the same source and external reference again returns the original
              record instead of creating a duplicate.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Permissions and errors</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p>
              Staff creation, revocation and regeneration need ticket:write + feedback:write.
              Listing needs ticket:read + feedback:read.
            </p>
            <p className="text-muted-foreground">
              Pending conflict: FEEDBACK_REQUEST_PENDING_EXISTS. Submitted requests cannot be
              revoked: FEEDBACK_REQUEST_NOT_REVOCABLE. Invalid public tokens are deliberately
              indistinguishable from missing ones.
            </p>
          </CardContent>
        </Card>
      </div>
    </SettingsLayout>
  );
}
