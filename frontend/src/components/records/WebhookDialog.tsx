"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field, FieldLabel, FieldError } from "@/components/ui/field";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useRecordTypes, useMappingProfiles } from "@/lib/hooks/records";
import { RecordWebhookResponse, WebhookAuthMode } from "@/types/records";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editing: RecordWebhookResponse | null;
  onSubmit: (data: {
    name: string;
    webhookKey: string;
    description?: string;
    recordTypeId: string;
    mappingProfileId?: string | null;
    isActive: boolean;
    authMode?: WebhookAuthMode;
  }) => void;
  isPending: boolean;
}

function slugify(label: string) {
  return label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "").replace(/__+/g, "_");
}
const KEY_REGEX = /^[a-z][a-z0-9_-]*$/;

export function WebhookDialog({ open, onOpenChange, editing, onSubmit, isPending }: Props) {
  const { data: typesData } = useRecordTypes(0, 100);
  const types = typesData?.data?.filter((t) => t.isActive) ?? [];

  const [name, setName] = useState("");
  const [webhookKey, setWebhookKey] = useState("");
  const [description, setDescription] = useState("");
  const [recordTypeId, setRecordTypeId] = useState("");
  const [mappingProfileId, setMappingProfileId] = useState<string>("none");
  const [isActive, setIsActive] = useState(true);
  const [authMode, setAuthMode] = useState<WebhookAuthMode>("NONE");
  const [touched, setTouched] = useState(false);

  const { data: mappingsData } = useMappingProfiles({ recordTypeId: recordTypeId || undefined, size: 100 });
  const mappings = mappingsData?.data ?? [];

  useEffect(() => {
    if (open) {
      if (editing) {
        setName(editing.name);
        setWebhookKey(editing.webhookKey);
        setDescription(editing.description || "");
        setRecordTypeId(editing.recordTypeId);
        setMappingProfileId(editing.mappingProfileId || "none");
        setIsActive(editing.isActive);
        setAuthMode((editing as any).authMode || "NONE");
      } else {
        setName("");
        setWebhookKey("");
        setDescription("");
        setRecordTypeId("");
        setMappingProfileId("none");
        setIsActive(true);
        setAuthMode("NONE");
      }
      setTouched(false);
    }
  }, [open, editing]);

  function handleRecordTypeChange(val: string) {
    setRecordTypeId(val);
    setMappingProfileId("none");
  }

  const keyError = !webhookKey.trim() ? "Webhook key is required" : webhookKey.length < 3 ? "Min 3 chars" : !KEY_REGEX.test(webhookKey) ? "Lowercase a-z, 0-9, _ or - starting with letter" : null;
  const nameError = !name.trim() ? "Name is required" : null;
  const recordTypeError = !recordTypeId ? "Record Type is required" : null;

  const canSave = !keyError && !nameError && !recordTypeError;

  function handleSave() {
    setTouched(true);
    if (!canSave) return;
    onSubmit({
      name: name.trim(),
      webhookKey: webhookKey.trim().toLowerCase(),
      description: description.trim() || undefined,
      recordTypeId,
      mappingProfileId: mappingProfileId !== "none" ? mappingProfileId : null,
      isActive,
      authMode,
    });
  }

  const endpointPreview = webhookKey ? `/api/v1/records/webhooks/${webhookKey.trim().toLowerCase()}` : "/api/v1/records/webhooks/{webhookKey}";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? "Edit Webhook" : "New Incoming Webhook"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Field>
            <FieldLabel>Name *</FieldLabel>
            <Input
              value={name}
              onChange={(e) => {
                const v = e.target.value;
                setName(v);
                if (!editing) setWebhookKey(slugify(v));
              }}
              placeholder="e.g. Dialer CDR"
            />
            {touched && nameError && <FieldError>{nameError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Webhook key *</FieldLabel>
            <Input value={webhookKey} onChange={(e) => setWebhookKey(e.target.value.toLowerCase())} placeholder="e.g. dialer_cdr" disabled={!!editing} className="font-mono text-sm" />
            <p className="text-xs text-muted-foreground">Public identifier for <span className="font-mono">{endpointPreview}</span>. {editing ? "Immutable." : "Lowercase, numbers, _ or -."}</p>
            {touched && keyError && <FieldError>{keyError}</FieldError>}
          </Field>
          <Field>
            <FieldLabel>Record Type *</FieldLabel>
            <Select value={recordTypeId} onValueChange={handleRecordTypeChange} disabled={!!editing}>
              <SelectTrigger>
                <SelectValue placeholder="Select Record Type" />
              </SelectTrigger>
              <SelectContent>
                {types.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name} ({t.key})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {touched && recordTypeError && <FieldError>{recordTypeError}</FieldError>}
            {editing && <p className="text-xs text-muted-foreground">Record Type cannot be changed after creation via this dialog – delete and recreate if needed.</p>}
          </Field>
          <Field>
            <FieldLabel>Mapping Profile (optional)</FieldLabel>
            <Select value={mappingProfileId} onValueChange={setMappingProfileId} disabled={!recordTypeId}>
              <SelectTrigger>
                <SelectValue placeholder={recordTypeId ? "Direct (no mapping)" : "Select Record Type first"} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">Direct – no mapping (payload already canonical)</SelectItem>
                {mappings.map((m) => (
                  <SelectItem key={m.id} value={m.id}>
                    {m.name} ({m.mappingKey}) – {m.mode} {m.isActive ? "" : "[inactive]"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">Only mapping profiles for the selected Record Type are shown. Leave Direct if payload already uses canonical field keys.</p>
          </Field>
          <Field>
            <FieldLabel>Description</FieldLabel>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Webhook for Sellspark dialer CDR" rows={2} />
          </Field>
          <Field>
            <FieldLabel>Authentication</FieldLabel>
            <Select value={authMode} onValueChange={(v) => setAuthMode(v as any)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NONE">NONE – no authentication (explicit)</SelectItem>
                <SelectItem value="API_KEY">API Key – X-Webhook-API-Key</SelectItem>
                <SelectItem value="HMAC_SHA256">HMAC SHA256 – X-Webhook-Signature</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              {authMode === "NONE" && "No authentication. Prefer API_KEY or HMAC for production."}
              {authMode === "API_KEY" && "Client must send X-Webhook-API-Key. Secret generated via Rotate."}
              {authMode === "HMAC_SHA256" && "Client must send X-Webhook-Signature = HMAC_SHA256(secret, rawBody) hex. Secret via Rotate."}
            </p>
          </Field>
          <div className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div>
              <FieldLabel>Active</FieldLabel>
              <p className="text-xs text-muted-foreground">Inactive webhooks are hidden from future ingestion.</p>
            </div>
            <Switch checked={isActive} onCheckedChange={setIsActive} />
          </div>
          <div className="rounded-lg bg-muted/30 border p-3">
            <p className="text-xs font-medium">Endpoint preview</p>
            <p className="font-mono text-xs mt-1 break-all">{endpointPreview}</p>
            <p className="text-xs text-muted-foreground mt-1">Future ingestion will be POST {endpointPreview}. Use Rotate Secret after creation to enable {authMode}.</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={isPending || !canSave}>
            {isPending ? "Saving..." : editing ? "Save Changes" : "Create"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
