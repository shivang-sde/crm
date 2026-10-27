"use client";

/**
 * VOICE-05 — Voice → Connections → Generic SIP (admin).
 *
 * Manages the shared tenant Generic SIP connection through the existing
 * ConnectorInstance + ConnectorCredential infrastructure (admin:settings).
 * The backend never returns secrets: the form tracks presence only, blank
 * means "keep existing", and fields are cleared after saving.
 */
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { voiceAdminApi, type AdminSipConnection } from "@/lib/api/voice";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { buildSafeSipConfig } from "@/lib/voice";
import {
  emptyIceServerForm,
  emptyVoiceConnectionForm,
  validateVoiceConnectionForm,
  type VoiceConnectionErrors,
  type VoiceConnectionForm,
} from "@/lib/voice/voice-connection";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SettingsLayout } from "@/components/settings/SettingsLayout";

function toForm(connection: AdminSipConnection): VoiceConnectionForm {
  const iceServers = (connection.config.iceServers ?? []).flatMap((server) => {
    const urls = Array.isArray(server.urls) ? server.urls : [server.urls];
    return urls
      .filter((url): url is string => typeof url === "string" && url.length > 0)
      .map((url) => ({
        urls: url,
        username: server.username ?? "",
        credential: "",
        credentialConfigured: server.credentialConfigured,
      }));
  });

  return {
    endpoint: connection.config.endpoint ?? "",
    sipDomain: connection.config.sipDomain ?? "",
    username: connection.config.username ?? "",
    secret: "",
    secretConfigured: connection.secretConfigured,
    iceServers,
  };
}

export default function VoiceConnectionsPage() {
  const { hasPermission } = usePermissions();
  const canManageVoice = hasPermission("admin", "settings");

  const [form, setForm] = useState<VoiceConnectionForm>(emptyVoiceConnectionForm);
  const [errors, setErrors] = useState<VoiceConnectionErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [secretScope, setSecretScope] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const connection = await voiceAdminApi.getGenericSipConnection();
      setForm(toForm(connection));
      setSecretScope(connection.secretScope);
      setSaved(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to load the Generic SIP connection.";
      setLoadError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const setField = <K extends keyof VoiceConnectionForm>(key: K, value: VoiceConnectionForm[K]) => {
    setForm((previous) => ({ ...previous, [key]: value }));
    setSaved(false);
  };

  const setIceField = (index: number, key: "urls" | "username" | "credential", value: string) => {
    setForm((previous) => ({
      ...previous,
      iceServers: previous.iceServers.map((server, current) =>
        current === index ? { ...server, [key]: value } : server
      ),
    }));
    setSaved(false);
  };

  const addIceServer = () => {
    setForm((previous) => ({ ...previous, iceServers: [...previous.iceServers, emptyIceServerForm()] }));
    setSaved(false);
  };

  const removeIceServer = (index: number) => {
    setForm((previous) => ({ ...previous, iceServers: previous.iceServers.filter((_, current) => current !== index) }));
    setSaved(false);
  };

  const handleSave = async (event: React.FormEvent) => {
    event.preventDefault();
    const validation = validateVoiceConnectionForm(form);
    setErrors(validation);
    if (Object.keys(validation).length > 0) {
      return;
    }

    // Compatibility check against the exact runtime contract before saving.
    const safe = buildSafeSipConfig("admin-preview", {
      endpoint: form.endpoint.trim(),
      sipDomain: form.sipDomain.trim(),
      username: form.username.trim(),
      iceServers: form.iceServers.map((server) => ({
        urls: server.urls.trim(),
        ...(server.username.trim() ? { username: server.username.trim() } : {}),
      })),
    });
    if (!safe) {
      setSaveError("Configuration is not compatible with the softphone runtime contract.");
      return;
    }

    setSaving(true);
    setSaveError(null);
    try {
      const connection = await voiceAdminApi.saveGenericSipConnection({
        endpoint: form.endpoint.trim(),
        sipDomain: form.sipDomain.trim(),
        username: form.username.trim(),
        iceServers: form.iceServers.map((server) => ({
          urls: server.urls.trim(),
          ...(server.username.trim() ? { username: server.username.trim() } : {}),
          ...(server.credential ? { credential: server.credential } : {}),
        })),
        ...(form.secret ? { secret: form.secret } : {}),
      });
      setForm(toForm(connection));
      setSecretScope(connection.secretScope);
      setSaved(true);
      toast.success("Configuration saved.");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to save the Generic SIP connection.";
      setSaveError(message);
      toast.error("Unable to save the Generic SIP connection.");
    } finally {
      setSaving(false);
    }
  };

  const handleRemoveSecret = async () => {
    const validation = validateVoiceConnectionForm({ ...form, secret: "", secretConfigured: true });
    if (validation.endpoint || validation.sipDomain || validation.username || validation.iceServers) {
      setErrors(validation);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const connection = await voiceAdminApi.saveGenericSipConnection({
        endpoint: form.endpoint.trim(),
        sipDomain: form.sipDomain.trim(),
        username: form.username.trim(),
        iceServers: form.iceServers.map((server) => ({
          urls: server.urls.trim(),
          ...(server.username.trim() ? { username: server.username.trim() } : {}),
        })),
        removeSecret: true,
      });
      setForm(toForm(connection));
      setSecretScope(connection.secretScope);
      setSaved(true);
      toast.success("Shared secret removed. Configuration saved.");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to remove the shared secret.";
      setSaveError(message);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <SettingsLayout>
        <p className="text-sm text-muted-foreground">Loading voice connection…</p>
      </SettingsLayout>
    );
  }

  if (!canManageVoice) {
    return (
      <SettingsLayout>
        <p className="text-sm text-muted-foreground">You do not have permission to manage voice connections.</p>
      </SettingsLayout>
    );
  }

  if (loadError) {
    return (
      <SettingsLayout>
        <div className="space-y-4">
          <h1 className="text-2xl font-semibold">Voice</h1>
          <p className="text-sm text-destructive">{loadError}</p>
          <Button type="button" variant="outline" onClick={() => void load()}>
            Retry
          </Button>
        </div>
      </SettingsLayout>
    );
  }

  const configured = form.endpoint.trim().length > 0 && form.secretConfigured;

  return (
    <SettingsLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold">Voice</h1>
          <p className="text-sm text-muted-foreground">
            Connections → Generic SIP. Shared tenant connection used by the softphone for browser calling.
          </p>
        </div>

        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <CardTitle>Generic SIP</CardTitle>
                <CardDescription>
                  SIP domain, WSS endpoint, extension credentials, and ICE servers stored in the existing
                  connector configuration. Secrets are never displayed after saving.
                </CardDescription>
              </div>
              <div className="flex gap-2">
                <Badge variant={configured ? "default" : "secondary"}>
                  {configured ? "Configured" : "Not configured"}
                </Badge>
                <Badge variant={form.secretConfigured ? "default" : "secondary"}>
                  {form.secretConfigured ? "Secret configured" : "Secret pending"}
                </Badge>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="sipDomain">SIP Domain</Label>
                  <Input
                    id="sipDomain"
                    value={form.sipDomain}
                    onChange={(event) => setField("sipDomain", event.target.value)}
                    placeholder="pbx.example.com"
                    autoComplete="off"
                  />
                  {errors.sipDomain ? <p className="text-xs text-destructive">{errors.sipDomain}</p> : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="endpoint">WSS URL</Label>
                  <Input
                    id="endpoint"
                    value={form.endpoint}
                    onChange={(event) => setField("endpoint", event.target.value)}
                    placeholder="wss://pbx.example.com:8089/ws"
                    autoComplete="off"
                    inputMode="url"
                  />
                  {errors.endpoint ? <p className="text-xs text-destructive">{errors.endpoint}</p> : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="username">Username / Extension</Label>
                  <Input
                    id="username"
                    value={form.username}
                    onChange={(event) => setField("username", event.target.value)}
                    placeholder="1001"
                    autoComplete="off"
                  />
                  {errors.username ? <p className="text-xs text-destructive">{errors.username}</p> : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="secret">Secret</Label>
                  <Input
                    id="secret"
                    type="password"
                    value={form.secret}
                    onChange={(event) => setField("secret", event.target.value)}
                    placeholder={form.secretConfigured ? "Leave blank to keep the existing secret" : "Required"}
                    autoComplete="new-password"
                  />
                  {errors.secret ? (
                    <p className="text-xs text-destructive">{errors.secret}</p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      {form.secretConfigured
                        ? `Shared secret is configured${secretScope ? ` (scope: ${secretScope})` : ""}. Agents with personal credentials keep overriding it.`
                        : "No shared secret is configured yet."}
                    </p>
                  )}
                </div>
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <Label>ICE Servers</Label>
                  <Button type="button" variant="outline" size="sm" onClick={addIceServer}>
                    <Plus className="mr-2 h-4 w-4" /> Add ICE server
                  </Button>
                </div>
                {form.iceServers.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No ICE servers configured. Host candidates only.</p>
                ) : null}
                {form.iceServers.map((server, index) => (
                  <div key={index} className="grid gap-3 rounded-md border p-3 md:grid-cols-[1fr_1fr_1fr_auto]">
                    <div className="space-y-2">
                      <Label htmlFor={`ice-url-${index}`}>STUN/TURN URL</Label>
                      <Input
                        id={`ice-url-${index}`}
                        value={server.urls}
                        onChange={(event) => setIceField(index, "urls", event.target.value)}
                        placeholder="stun:stun.example.com:3478"
                        autoComplete="off"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor={`ice-user-${index}`}>Username</Label>
                      <Input
                        id={`ice-user-${index}`}
                        value={server.username}
                        onChange={(event) => setIceField(index, "username", event.target.value)}
                        autoComplete="off"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor={`ice-cred-${index}`}>Credential</Label>
                      <Input
                        id={`ice-cred-${index}`}
                        type="password"
                        value={server.credential}
                        onChange={(event) => setIceField(index, "credential", event.target.value)}
                        placeholder={server.credentialConfigured ? "Leave blank to keep" : "Optional"}
                        autoComplete="new-password"
                      />
                    </div>
                    <div className="flex items-end">
                      <Button type="button" variant="outline" size="icon" onClick={() => removeIceServer(index)} aria-label="Remove ICE server">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}
                {errors.iceServers ? <p className="text-xs text-destructive">{errors.iceServers}</p> : null}
              </div>

              {saveError ? <p className="text-sm text-destructive">{saveError}</p> : null}
              {saved ? <p className="text-sm text-muted-foreground">Configuration saved.</p> : null}

              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={saving}>
                  {saving ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Saving…
                    </>
                  ) : (
                    "Save Connection"
                  )}
                </Button>
                {form.secretConfigured ? (
                  <Button type="button" variant="outline" onClick={() => void handleRemoveSecret()} disabled={saving}>
                    Remove Secret
                  </Button>
                ) : null}
              </div>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Runtime status</CardTitle>
            <CardDescription>
              Configuration state only. Live SIP registration is owned by the softphone runtime, not this page.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p className="text-muted-foreground">
              Configuration: <span className="font-medium text-foreground">{configured ? "Configured" : "Not configured"}</span>
              {" · "}Runtime: <span className="font-medium text-foreground">Not connected (softphone)</span>
            </p>
            <p className="text-muted-foreground">
              Agents can override the shared secret with personal credentials under{" "}
              <Link href="/settings/calling" className="underline">Settings → Calling</Link>. Open{" "}
              <Link href="/softphone" className="underline">the softphone</Link> to register and call.
            </p>
          </CardContent>
        </Card>
      </div>
    </SettingsLayout>
  );
}
