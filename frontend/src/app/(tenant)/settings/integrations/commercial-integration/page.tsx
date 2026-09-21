"use client";

import { useState, useEffect } from "react";
import { toast } from "sonner";
import { Copy, Key, AlertTriangle, CheckCircle2, XCircle } from "lucide-react";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Input } from "@/components/ui/input";
import { useCommercialKeyStatus, useCommercialRotate } from "@/lib/hooks/commercial-integration";
import { apiErrorMessage } from "@/lib/api/api-utils";

export default function CommercialIntegrationPage() {
  const { data: status, isLoading, error, refetch } = useCommercialKeyStatus();
  const rotate = useCommercialRotate();
  const [plainKey, setPlainKey] = useState<string | null>(null);
  const [plainPrefix, setPlainPrefix] = useState<string | null>(null);
  const [baseUrl, setBaseUrl] = useState<string>("");

  useEffect(() => {
    const envUrl = process.env.NEXT_PUBLIC_API_URL;
    if (envUrl && envUrl !== "/api/v1") {
      setBaseUrl(envUrl.replace(/\/api\/v1\/?$/, ""));
    } else if (typeof window !== "undefined") {
      setBaseUrl(window.location.origin);
    }
  }, []);

  const apiErrCode = (error as any)?.response?.data?.error?.code;
  const isFeatureDisabled = apiErrCode === "FEATURE_DISABLED";

  const handleGenerate = async () => {
    try {
      const res = await rotate.mutateAsync();
      setPlainKey(res.apiKey);
      setPlainPrefix(res.prefix);
      toast.success("API key generated");
    } catch (e) {
      toast.error(apiErrorMessage(e, "Failed to generate API key"));
    }
  };

  const handleRotate = async () => {
    try {
      const res = await rotate.mutateAsync();
      setPlainKey(res.apiKey);
      setPlainPrefix(res.prefix);
      toast.success("API key rotated");
    } catch (e) {
      toast.error(apiErrorMessage(e, "Failed to rotate API key"));
    }
  };

  const copy = async (text: string, msg: string) => {
    await navigator.clipboard.writeText(text);
    toast.success(msg);
  };

  if (isFeatureDisabled) {
    return (
      <SettingsLayout>
        <div className="max-w-3xl space-y-6">
          <h1 className="text-2xl font-semibold">Commercial Integration</h1>
          <Alert>
            <AlertTriangle className="h-4 w-4" />
            <AlertDescription>
              Commercial Integration is disabled for this deployment. Contact your administrator to enable{" "}
              <code className="rounded bg-muted px-1">COMMERCIAL_INTEGRATION_ENABLED</code>.
            </AlertDescription>
          </Alert>
        </div>
      </SettingsLayout>
    );
  }

  return (
    <SettingsLayout>
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight flex items-center gap-2">
            <Key className="h-6 w-6 text-indigo-600" />
            Commercial Integration
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Connect your quotation and invoice platform to this CRM. The external platform uses the API key
            below to send customer, quotation, and invoice data into the CRM.
          </p>
        </div>

        {/* Connection status */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Connection status</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-6 w-32" />
            ) : error ? (
              <div className="flex items-center gap-2 text-sm text-destructive">
                <XCircle className="h-4 w-4" /> Failed to load status
                <Button variant="outline" size="sm" onClick={() => refetch()}>
                  Retry
                </Button>
              </div>
            ) : status?.configured ? (
              <span className="inline-flex items-center gap-2 text-sm font-medium text-emerald-700">
                <CheckCircle2 className="h-4 w-4" /> Configured
              </span>
            ) : (
              <span className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground">
                <XCircle className="h-4 w-4" /> Not configured
              </span>
            )}
            <p className="mt-2 text-xs text-muted-foreground">
              The API key only establishes credentials. It does not mean the external platform is currently connected.
            </p>
          </CardContent>
        </Card>

        {/* API Key section */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Commercial API Key</CardTitle>
            <CardDescription>Tenant-scoped credential for your quotation/invoice platform. Treat like a password.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {isLoading ? (
              <Skeleton className="h-10 w-full" />
            ) : plainKey ? (
              <div className="space-y-3">
                <Alert className="border-amber-200 bg-amber-50">
                  <AlertTriangle className="h-4 w-4 text-amber-600" />
                  <AlertDescription className="text-amber-800 text-xs">
                    This key will not be shown again after you leave or refresh this page. Copy it now and store it securely in your external platform.
                  </AlertDescription>
                </Alert>
                <div className="space-y-1">
                  <label className="text-xs font-medium">Your new API key</label>
                  <p className="text-xs text-muted-foreground">This key will not be shown again after you leave or refresh this page.</p>
                  <div className="flex gap-2">
                    <div className="flex-1 min-w-0 rounded-md border bg-muted px-3 py-2 font-mono text-xs break-all select-all">
                      {plainKey}
                    </div>
                    <Button variant="outline" className="shrink-0" onClick={() => copy(plainKey, "API key copied")}>
                      <Copy className="h-4 w-4" /> Copy<span className="hidden sm:inline">&nbsp;API Key</span>
                    </Button>
                  </div>
                  {plainPrefix && <p className="text-xs text-muted-foreground">Prefix: {plainPrefix}</p>}
                </div>
                <Button variant="ghost" size="sm" onClick={() => setPlainKey(null)}>
                  Dismiss
                </Button>
              </div>
            ) : status?.configured ? (
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <Input readOnly value="••••••••••••••••••••••••••••••••••••••••••••••••••••••••" className="font-mono" />
                </div>
                {status.keyPrefix && <p className="text-xs text-muted-foreground">Prefix: {status.keyPrefix}</p>}
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button variant="outline" disabled={rotate.isPending}>
                      {rotate.isPending ? "Rotating..." : "Rotate API Key"}
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Rotate API key?</AlertDialogTitle>
                      <AlertDialogDescription>
                        Rotating this API key will invalidate the existing key. Your quotation/invoice platform must be updated with the new key before it can continue sending data.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Cancel</AlertDialogCancel>
                      <AlertDialogAction onClick={handleRotate}>Rotate API Key</AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-sm text-muted-foreground">Generate an API key for your quotation/invoice platform.</p>
                <Button onClick={handleGenerate} disabled={rotate.isPending}>
                  {rotate.isPending ? "Generating..." : "Generate API Key"}
                </Button>
              </div>
            )}
            <Alert>
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription className="text-xs">
                Treat this API key like a password. Anyone who has it can authenticate as your commercial integration for this tenant.
              </AlertDescription>
            </Alert>
          </CardContent>
        </Card>

        {/* CRM Base URL */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">CRM Base URL</CardTitle>
            <CardDescription>Base URL your external platform should use. Full paths are shown below.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="flex gap-2">
              <Input readOnly value={baseUrl || "—"} className="font-mono text-xs" />
              <Button variant="outline" onClick={() => baseUrl && copy(baseUrl, "Base URL copied")} disabled={!baseUrl}>
                <Copy className="h-4 w-4" /> Copy
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Connection instructions */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Connect your quotation/invoice platform</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            <ol className="list-decimal space-y-1 pl-5">
              <li>Store the Commercial API key securely on your server.</li>
              <li>Send it as header <code className="rounded bg-muted px-1">X-API-Key</code>.</li>
              <li>Sync the customer before sending its quotation/invoice.</li>
              <li>Use the customer external ID consistently.</li>
              <li>Send quotation/invoice updates using the same external document ID.</li>
              <li>Send <code className="rounded bg-muted px-1">pdfUrl</code> when a PDF is available.</li>
              <li>Implement retry/backoff for transient failures.</li>
            </ol>
            <div className="rounded-md bg-muted p-3 font-mono text-xs space-y-1">
              <div>POST /api/v1/integrations/commercial/customers/sync</div>
              <div>POST /api/v1/integrations/commercial/documents/sync</div>
            </div>
            <p className="text-xs text-muted-foreground">
              Refer to your integration handoff documentation for full API details.
            </p>
          </CardContent>
        </Card>
      </div>
    </SettingsLayout>
  );
}
