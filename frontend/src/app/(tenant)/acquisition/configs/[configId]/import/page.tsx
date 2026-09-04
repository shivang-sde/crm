"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Upload, FileSpreadsheet, CheckCircle2, AlertTriangle, XCircle, Eye, ExternalLink, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAcquisitionConfig, useLeadIngestionMappings, useLeadIngestionTargetFields } from "@/lib/hooks/acquisition";
import { usePreviewImport, useImportCsv } from "@/lib/hooks/acquisition";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { CsvImportPreviewResponse, CsvImportResponse } from "@/types/acquisition";

function AutoMappingHint({ columns, targetFields }: { columns: string[]; targetFields: { fieldKey: string }[] }) {
  const suggestions = useMemo(() => {
    const lowerTargets = new Map(targetFields.map((t) => [t.fieldKey.toLowerCase(), t.fieldKey]));
    const hints: { column: string; suggested: string | null }[] = [];
    for (const col of columns) {
      const norm = col.toLowerCase().replace(/[^a-z0-9]/g, "");
      let found: string | null = null;
      for (const [k, v] of lowerTargets) {
        const nk = k.replace(/[^a-z0-9]/g, "");
        if (nk === norm || nk === `first${norm}` || nk === `last${norm}`) {
          found = v;
          break;
        }
        // email variations
        if (["email", "emailaddress", "email_address"].includes(norm) && ["email"].includes(nk)) found = v;
        if (["phone", "mobile", "telephone", "mobilephone"].includes(norm) && ["phone"].includes(nk)) found = v;
        if (["firstname", "first_name", "firstName"].includes(col.toLowerCase()) && nk === "firstname") found = v;
        if (["lastname", "last_name", "lastName"].includes(col.toLowerCase()) && nk === "lastname") found = v;
        if (["company", "companyname", "account", "accountname"].includes(norm) && nk === "company") found = v;
      }
      hints.push({ column: col, suggested: found });
    }
    return hints;
  }, [columns, targetFields]);

  const hasSuggestions = suggestions.some((s) => s.suggested);
  if (!hasSuggestions) return null;
  return (
    <div className="rounded-md border bg-blue-50 p-3 dark:bg-blue-950/20">
      <p className="text-xs font-medium text-blue-700 dark:text-blue-300">Auto-mapping suggestions (review before import)</p>
      <div className="mt-1 flex flex-wrap gap-2">
        {suggestions
          .filter((s) => s.suggested)
          .map((s) => (
            <Badge key={s.column} variant="outline" className="text-xs">
              {s.column} → {s.suggested}
            </Badge>
          ))}
      </div>
      <p className="text-xs text-muted-foreground mt-1">These are suggestions only — configure mapping explicitly in the mapping editor.</p>
    </div>
  );
}

export default function CsvImportPage() {
  const params = useParams<{ configId: string }>();
  const configId = params?.configId ?? "";
  const { canViewAcquisition, canEditAcquisition } = usePermissions();

  const { data: config, isLoading: configLoading } = useAcquisitionConfig(configId);
  const mappingsQ = useLeadIngestionMappings(configId);
  const targetFieldsQ = useLeadIngestionTargetFields(configId);

  const [file, setFile] = useState<File | null>(null);
  const [previewData, setPreviewData] = useState<CsvImportPreviewResponse | null>(null);
  const [importResult, setImportResult] = useState<CsvImportResponse | null>(null);

  const previewMut = usePreviewImport(configId);
  const importMut = useImportCsv(configId);

  const targetFields = targetFieldsQ.data?.data ?? [];
  const mappings = mappingsQ.data?.data ?? [];
  const activeMappings = mappings.filter((m) => m.active);

  if (!canViewAcquisition) {
    return (
      <div className="p-6">
        <p className="text-sm text-muted-foreground">You do not have permission to view imports.</p>
      </div>
    );
  }

  if (configLoading) {
    return <div className="p-6"><p className="text-sm text-muted-foreground">Loading source…</p></div>;
  }

  if (!config || config.transportType !== "IMPORT") {
    return (
      <div className="p-6 space-y-3">
        <Link href={`/acquisition/configs/${configId}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to source
        </Link>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm font-medium">This source is not a CSV Import</p>
            <p className="text-xs text-muted-foreground">Create an IMPORT source to use CSV import.</p>
            <Link href="/acquisition"><Button variant="outline" size="sm" className="mt-2">Go to acquisition</Button></Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    setFile(f);
    setPreviewData(null);
    setImportResult(null);
  };

  const handlePreview = async () => {
    if (!file) {
      toast.error("Select a CSV file first");
      return;
    }
    try {
      const data = await previewMut.mutateAsync(file);
      setPreviewData(data);
      toast.success(`Detected ${data.columnCount} columns, ${data.rowCount} rows`);
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message ?? e?.message ?? "Preview failed";
      toast.error(msg);
    }
  };

  const handleImport = async () => {
    if (!file) {
      toast.error("Select a CSV file first");
      return;
    }
    try {
      const result = await importMut.mutateAsync(file);
      setImportResult(result);
      if (result.created > 0) toast.success(`Imported: ${result.created} created`);
      else toast.success("Import completed");
    } catch (e: any) {
      const msg = e?.response?.data?.error?.message ?? e?.message ?? "Import failed";
      toast.error(msg);
    }
  };

  const requiredMissing = targetFields.filter(
    (tf) => tf.required && !activeMappings.some((m) => m.targetType === tf.targetType && m.targetField === tf.fieldKey)
  );

  return (
    <div className="space-y-6 p-6">
      <div>
        <Link href={`/acquisition/configs/${configId}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to source
        </Link>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <FileSpreadsheet className="h-5 w-5" /> Import Leads from CSV
        </h1>
        <p className="text-sm text-muted-foreground">
          Upload your CSV file to create leads. We&apos;ll map your columns to CRM fields and show you what will happen before importing.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Upload className="h-4 w-4" /> Upload CSV
          </CardTitle>
          <CardDescription>Header row required, alphanumeric/underscore column names, max 50 columns, 5000 rows, 10MB. Quoted commas handled.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-2">
            <Label htmlFor="csvFile">CSV file</Label>
            <Input id="csvFile" type="file" accept=".csv,text/csv" onChange={handleFileChange} />
            {file && (
              <p className="text-xs text-muted-foreground">
                Selected: <span className="font-medium">{file.name}</span> · {(file.size / 1024).toFixed(1)} KB
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={handlePreview} disabled={!file || previewMut.isPending} variant="outline">
              {previewMut.isPending ? "Discovering…" : "Discover & Preview"}
            </Button>
            {previewData && (
              <Badge variant="outline">
                {previewData.columnCount} cols · {previewData.rowCount} rows
              </Badge>
            )}
          </div>
          {!canEditAcquisition && (
            <p className="text-xs text-amber-600">You need acquisition:write to import.</p>
          )}
        </CardContent>
      </Card>

      {previewData && (
        <>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Detected columns</CardTitle>
              <CardDescription>
                Rows detected: {previewData.rowCount} · Columns: {previewData.columnCount} · First row sample shown. Map these columns to CRM fields.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex flex-wrap gap-2">
                {previewData.columns.map((col) => (
                  <Badge key={col} variant="outline">
                    {col}
                  </Badge>
                ))}
              </div>
              <div className="grid gap-2 md:grid-cols-2">
                {previewData.samples.map((s) => (
                  <div key={s.column} className="rounded-md border p-2 text-xs">
                    <p className="font-medium">{s.column}</p>
                    <p className="text-muted-foreground">sample: {s.sampleValue ?? "—"} <span className="ml-1">({s.detectedType})</span></p>
                  </div>
                ))}
              </div>
              <AutoMappingHint columns={previewData.columns} targetFields={targetFields} />
              {requiredMissing.length > 0 && (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-2 dark:bg-amber-950/20">
                  <p className="text-xs font-medium text-amber-700">Required fields not yet mapped</p>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {requiredMissing.map((r) => (
                      <Badge key={`${r.targetType}:${r.fieldKey}`} variant="outline" className="border-amber-300 text-amber-700">
                        {r.fieldKey}
                      </Badge>
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    Map these in <Link href={`/acquisition/configs/${configId}/mappings`} className="underline">mapping editor</Link> before import to avoid rejections.
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Preview (first 10 rows) — via current mapping</CardTitle>
              <CardDescription>Raw → Mapped → Normalized → Validation. Shows actual transform execution.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {previewData.previewRows.length === 0 ? (
                <p className="text-sm text-muted-foreground">No preview rows.</p>
              ) : (
                <div className="space-y-3">
                  {previewData.previewRows.map((row) => (
                    <div key={row.rowNumber} className="rounded-md border p-3">
                      <div className="flex items-center gap-2 mb-2">
                        <Badge variant="outline">Row {row.rowNumber}</Badge>
                        <Badge
                          variant={
                            row.status === "VALID"
                              ? "default"
                              : row.status === "REJECTED"
                                ? "destructive"
                                : "secondary"
                          }
                          className="text-xs"
                        >
                          {row.status}
                          {row.failureStage ? ` · ${row.failureStage}` : ""}
                        </Badge>
                      </div>
                      <div className="grid gap-2 md:grid-cols-2 text-xs">
                        <div>
                          <p className="font-medium">CSV values</p>
                          <pre className="rounded bg-muted/30 p-2 overflow-auto max-h-32">{JSON.stringify(row.rawPayload, null, 2)}</pre>
                        </div>
                        <div>
                          <p className="font-medium">Mapped (standard/system/custom)</p>
                          <pre className="rounded bg-muted/30 p-2 overflow-auto max-h-32">{JSON.stringify({ standard: row.mapped?.standardFields, system: row.mapped?.systemFields, custom: row.mapped?.customFields, errors: row.mapped?.errors }, null, 2)}</pre>
                        </div>
                      </div>
                      {row.validated && (
                        <div className="mt-2">
                          <p className="text-xs font-medium">Validated</p>
                          <pre className="rounded bg-muted/30 p-2 text-xs overflow-auto max-h-32">{JSON.stringify({ firstName: row.validated.firstName, email: row.validated.email, phone: row.validated.phone, company: row.validated.company, errors: row.validated.errors }, null, 2)}</pre>
                          {(row.validated.errors ?? []).length > 0 && (
                            <div className="mt-1">
                              {(row.validated.errors ?? []).map((e, idx) => (
                                <p key={idx} className="text-xs text-red-600">
                                  {e.field}: {e.message} ({e.code})
                                </p>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <Link href={`/acquisition/configs/${configId}/mappings`}>
                  <Button variant="outline" size="sm">
                    <Eye className="mr-1 h-3 w-3" /> Configure mapping
                  </Button>
                </Link>
                <Button onClick={handleImport} disabled={!file || importMut.isPending || !canEditAcquisition}>
                  {importMut.isPending ? "Importing…" : `Import ${previewData.rowCount} rows`}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">Import will create one acquisition event per row and run the universal pipeline (partial success allowed).</p>
            </CardContent>
          </Card>
        </>
      )}

      {importResult && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 text-green-600" /> Import completed
            </CardTitle>
            <CardDescription>
              {importResult.fileName} · {importResult.totalRows} rows processed
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid gap-2 md:grid-cols-4">
              <div className="rounded-md border bg-green-50 p-3 text-center dark:bg-green-950/20">
                <p className="text-2xl font-bold text-green-700">{importResult.created}</p>
                <p className="text-xs text-green-700">Created</p>
              </div>
              <div className="rounded-md border bg-amber-50 p-3 text-center dark:bg-amber-950/20">
                <p className="text-2xl font-bold text-amber-700">{importResult.duplicate}</p>
                <p className="text-xs text-amber-700">Duplicate</p>
              </div>
              <div className="rounded-md border bg-red-50 p-3 text-center dark:bg-red-950/20">
                <p className="text-2xl font-bold text-red-700">{importResult.rejected}</p>
                <p className="text-xs text-red-700">Rejected</p>
              </div>
              <div className="rounded-md border bg-muted p-3 text-center">
                <p className="text-2xl font-bold">{importResult.failed}</p>
                <p className="text-xs text-muted-foreground">Failed</p>
              </div>
            </div>

            <div>
              <p className="text-sm font-medium">Row-level results (first {importResult.rows.length})</p>
              <div className="mt-2 space-y-2 max-h-96 overflow-auto">
                {importResult.rows.map((r) => (
                  <div key={r.rowNumber} className="flex items-center justify-between rounded-md border p-2 text-xs">
                    <div className="flex items-center gap-2 min-w-0">
                      <Badge variant="outline">Row {r.rowNumber}</Badge>
                      <Badge
                        variant={
                          r.status === "PROCESSED"
                            ? "default"
                            : r.status === "DUPLICATE"
                              ? "secondary"
                              : r.status === "REJECTED"
                                ? "destructive"
                                : "outline"
                        }
                      >
                        {r.status}
                        {r.failureStage ? ` · ${r.failureStage}` : ""}
                      </Badge>
                      <span className="truncate max-w-[240px]">{r.errorMessage ?? r.errorCode ?? (r.leadId ? `Lead ${r.leadId.slice(0, 8)}` : "—")}</span>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {r.leadId && (
                        <Link href={`/leads/${r.leadId}`} className="underline flex items-center gap-1">
                          View lead <ExternalLink className="h-3 w-3" />
                        </Link>
                      )}
                      {r.eventId && (
                        <Link href={`/acquisition/configs/${configId}/events/${r.eventId}`} className="underline">
                          Event
                        </Link>
                      )}
                    </div>
                  </div>
                ))}
              </div>
              {importResult.rows.length < importResult.totalRows && (
                <p className="text-xs text-muted-foreground mt-1">
                  Showing {importResult.rows.length} of {importResult.totalRows} rows. View all in <Link href={`/acquisition/configs/${configId}/events`} className="underline">Events</Link>.
                </p>
              )}
            </div>

            <div className="flex gap-2">
              <Link href={`/acquisition/configs/${configId}/events`}>
                <Button variant="outline" size="sm">
                  <RefreshCw className="mr-1 h-3 w-3" /> View events
                </Button>
              </Link>
              <Link href={`/acquisition/configs/${configId}/mappings`}>
                <Button variant="outline" size="sm">Back to mapping</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
