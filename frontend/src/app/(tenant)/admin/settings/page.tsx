"use client";

import { useEffect, useMemo, useState } from "react";
import { Copy, Plus, RefreshCw } from "lucide-react";
import { api } from "@/lib/api/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { usePermissions } from "@/lib/hooks/usePermissions";

interface ProviderSummary {
  id: string;
  providerKey: string;
  displayName: string;
  category: string;
  supportedActions: string[];
  supportedTriggers: string[];
  active: boolean;
}

interface ConnectorInstanceSummary {
  id: string;
  providerKey: string;
  providerName: string;
  connectorName: string;
  environment: string;
  baseUrl: string;
  active: boolean;
  config: Record<string, unknown>;
}

interface CredentialStatus {
  configured: boolean;
  authType: string;
}

interface WebhookConfig {
  id?: string;
  callConnectUrl: string;
  cdrUrl: string;
  targetUrl?: string;
  active: boolean;
  verificationMode?: string;
  configured: boolean;
  webhookName?: string;
}

interface TriggerRule {
  id: string;
  triggerKey: string;
  name: string;
  active: boolean;
  direction: string;
  resolveBy: string;
  entityType?: string;
  openAction: string;
  displayMode: string;
  priority: number;
}

interface LayoutConfig {
  id?: string;
  displayMode: string;
  active: boolean;
  showEntityDetails: boolean;
  showCallHistory: boolean;
  showNotes: boolean;
  showDisposition: boolean;
}

const emptyProviderForm = {
  providerKey: "",
  name: "",
  environment: "PRODUCTION",
  baseUrl: "",
  active: true,
};

const emptyTriggerForm = {
  name: "",
  triggerKey: "call-connect",
  direction: "BOTH",
  resolveBy: "ENTITY",
  entityType: "",
  openAction: "NO_ACTION",
  displayMode: "PAGE",
  route: "",
  priority: 0,
  active: true,
};

export default function CallingSettingsPage() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [instances, setInstances] = useState<ConnectorInstanceSummary[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [providerForm, setProviderForm] = useState(emptyProviderForm);
  const [credentialStatus, setCredentialStatus] = useState<Record<string, CredentialStatus>>({});
  const [credentialValues, setCredentialValues] = useState<Record<string, string>>({});
  const [webhookConfig, setWebhookConfig] = useState<Record<string, WebhookConfig>>({});
  const [webhookForm, setWebhookForm] = useState({ webhookName: "", targetUrl: "", verificationMode: "HMAC", active: true });
  const [secretNotice, setSecretNotice] = useState<string | null>(null);
  const [triggers, setTriggers] = useState<TriggerRule[]>([]);
  const [triggerForm, setTriggerForm] = useState(emptyTriggerForm);
  const [editingTriggerId, setEditingTriggerId] = useState<string | null>(null);
  const [layoutConfig, setLayoutConfig] = useState<LayoutConfig>({ displayMode: "PAGE", active: true, showEntityDetails: true, showCallHistory: true, showNotes: true, showDisposition: true });
  const [loading, setLoading] = useState(true);
  const [savingInstance, setSavingInstance] = useState(false);
  const { hasPermission } = usePermissions();
  const canManageCallingSettings = hasPermission("call", "write");
  const canViewCallingSettings = hasPermission("call", "read");
  const [savingCredentials, setSavingCredentials] = useState(false);
  const [savingWebhook, setSavingWebhook] = useState(false);
  const [savingTrigger, setSavingTrigger] = useState(false);
  const [savingLayout, setSavingLayout] = useState(false);

  const selectedInstance = useMemo(
    () => instances.find((instance) => instance.id === selectedInstanceId) ?? null,
    [instances, selectedInstanceId]
  );

  const selectedProvider = providers.find((provider) => provider.providerKey === providerForm.providerKey) ?? null;

  const credentialFields = useMemo(() => {
    if (selectedProvider?.providerKey === "sellspark_voice") {
      return [
        { key: "userId", label: "User ID / Agent ID", type: "text" as const },
        { key: "password", label: "Password", type: "password" as const },
      ];
    }

    return [
      { key: "apiKey", label: "API Key", type: "password" as const },
      { key: "token", label: "Token", type: "password" as const },
    ];
  }, [selectedProvider]);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [providersResponse, instancesResponse, triggersResponse, layoutResponse] = await Promise.all([
          api.get<ProviderSummary[]>("/integrations/providers"),
          api.get<ConnectorInstanceSummary[]>("/integrations/connector-instances"),
          api.get<TriggerRule[]>("/call-settings/connect-triggers"),
          api.get<LayoutConfig>("/call-settings/layout-config"),
        ]);

        const providerList = providersResponse.data ?? [];
        const instanceList = instancesResponse.data ?? [];
        setProviders(providerList);
        setInstances(instanceList);
        setTriggers(triggersResponse.data ?? []);
        setLayoutConfig(layoutResponse.data ?? { displayMode: "PAGE", active: true, showEntityDetails: true, showCallHistory: true, showNotes: true, showDisposition: true });

        if (instanceList.length > 0) {
          const initialSelection = instanceList[0].id;
          setSelectedInstanceId(initialSelection);
          setProviderForm({
            providerKey: instanceList[0].providerKey ?? providerList[0]?.providerKey ?? "",
            name: instanceList[0].connectorName ?? "",
            environment: instanceList[0].environment ?? "PRODUCTION",
            baseUrl: instanceList[0].baseUrl ?? "",
            active: instanceList[0].active,
          });
        } else {
          setProviderForm({ ...emptyProviderForm, providerKey: providerList[0]?.providerKey ?? "" });
        }
      } catch (error) {
        console.error("Failed to load calling settings", error);
      } finally {
        setLoading(false);
      }
    };

    void loadData();
  }, []);

  useEffect(() => {
    if (!selectedInstance) {
      return;
    }

    const loadDetails = async () => {
      try {
        const [credentialResponse, webhookResponse] = await Promise.all([
          api.get<CredentialStatus>(`/integrations/connector-instances/${selectedInstance.id}/credentials`),
          api.get<WebhookConfig>(`/integrations/connector-instances/${selectedInstance.id}/webhook-config`),
        ]);

        setCredentialStatus((previous) => ({ ...previous, [selectedInstance.id]: credentialResponse.data ?? { configured: false, authType: "PROVIDER_SPECIFIC" } }));
        setWebhookConfig((previous) => ({ ...previous, [selectedInstance.id]: webhookResponse.data ?? { callConnectUrl: "", cdrUrl: "", active: false, configured: false } }));
        setWebhookForm({
          webhookName: webhookResponse.data?.webhookName ?? "",
          targetUrl: webhookResponse.data?.targetUrl ?? "",
          verificationMode: webhookResponse.data?.verificationMode ?? "HMAC",
          active: webhookResponse.data?.active ?? true,
        });
        setCredentialValues((previous) => ({ ...previous, [selectedInstance.id]: "" }));
      } catch (error) {
        console.error("Failed to load connector details", error);
      }
    };

    void loadDetails();
  }, [selectedInstance]);

  const selectInstance = (instanceId: string | null) => {
    setSelectedInstanceId(instanceId);
    const instance = instances.find((item) => item.id === instanceId);
    if (instance) {
      setProviderForm({
        providerKey: instance.providerKey ?? "",
        name: instance.connectorName ?? "",
        environment: instance.environment ?? "PRODUCTION",
        baseUrl: instance.baseUrl ?? "",
        active: instance.active,
      });
    } else {
      setProviderForm({ ...emptyProviderForm, providerKey: providers[0]?.providerKey ?? "" });
    }
  };

  const resetTriggerForm = () => {
    setTriggerForm(emptyTriggerForm);
    setEditingTriggerId(null);
  };

  const handleSaveInstance = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedInstance && !providerForm.providerKey) {
      return;
    }

    setSavingInstance(true);
    try {
      const payload = {
        providerKey: providerForm.providerKey,
        name: providerForm.name,
        environment: providerForm.environment,
        baseUrl: providerForm.baseUrl,
        config: {},
        active: providerForm.active,
      };

      const response = selectedInstance
        ? await api.put<ConnectorInstanceSummary>(`/integrations/connector-instances/${selectedInstance.id}`, payload)
        : await api.post<ConnectorInstanceSummary>("/integrations/connector-instances", payload);

      const createdInstance = response.data;
      const refreshed = await api.get<ConnectorInstanceSummary[]>("/integrations/connector-instances");
      const nextInstances = refreshed.data ?? [];
      setInstances(nextInstances);
      const nextSelection = createdInstance?.id ?? nextInstances[0]?.id ?? null;
      setSelectedInstanceId(nextSelection);
      if (nextSelection) {
        const nextSelected = nextInstances.find((item) => item.id === nextSelection);
        if (nextSelected) {
          setProviderForm({
            providerKey: nextSelected.providerKey ?? "",
            name: nextSelected.connectorName ?? "",
            environment: nextSelected.environment ?? "PRODUCTION",
            baseUrl: nextSelected.baseUrl ?? "",
            active: nextSelected.active,
          });
        }
      }
    } catch (error) {
      console.error("Failed to save connector instance", error);
    } finally {
      setSavingInstance(false);
    }
  };

  const handleToggleInstance = async (instanceId: string, active: boolean) => {
    try {
      await api.patch(`/integrations/connector-instances/${instanceId}/status`, { active });
      setInstances((previous) => previous.map((instance) => instance.id === instanceId ? { ...instance, active } : instance));
    } catch (error) {
      console.error("Failed to update connector status", error);
    }
  };

  const handleSaveCredentials = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedInstance) {
      return;
    }

    setSavingCredentials(true);
    try {
      const credentialPayload = {
        authType: selectedProvider?.providerKey === "sellspark_voice" ? "PROVIDER_SPECIFIC" : "PROVIDER_SPECIFIC",
        values: Object.fromEntries(credentialFields.map((field) => [field.key, credentialValues[field.key] ?? ""])),
      };
      await api.put(`/integrations/connector-instances/${selectedInstance.id}/credentials`, credentialPayload);
      const response = await api.get<CredentialStatus>(`/integrations/connector-instances/${selectedInstance.id}/credentials`);
      setCredentialStatus((previous) => ({ ...previous, [selectedInstance.id]: response.data ?? { configured: false, authType: "PROVIDER_SPECIFIC" } }));
      setCredentialValues((previous) => ({ ...previous, [selectedInstance.id]: "" }));
    } catch (error) {
      console.error("Failed to save credentials", error);
    } finally {
      setSavingCredentials(false);
    }
  };

  const handleSaveWebhook = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedInstance) {
      return;
    }

    setSavingWebhook(true);
    try {
      const payload = {
        webhookName: webhookForm.webhookName,
        targetUrl: webhookForm.targetUrl,
        verificationMode: webhookForm.verificationMode,
        eventTypes: { callConnect: true, cdr: true },
        active: webhookForm.active,
      };
      const response = await api.put<WebhookConfig>(`/integrations/connector-instances/${selectedInstance.id}/webhook-config`, payload);
      setWebhookConfig((previous) => ({ ...previous, [selectedInstance.id]: response.data ?? { callConnectUrl: "", cdrUrl: "", active: false, configured: false } }));
      setWebhookForm({
        webhookName: response.data?.webhookName ?? webhookForm.webhookName,
        targetUrl: response.data?.targetUrl ?? webhookForm.targetUrl,
        verificationMode: response.data?.verificationMode ?? webhookForm.verificationMode,
        active: response.data?.active ?? webhookForm.active,
      });
    } catch (error) {
      console.error("Failed to save webhook config", error);
    } finally {
      setSavingWebhook(false);
    }
  };

  const handleRegenerateSecret = async () => {
    if (!selectedInstance) {
      return;
    }

    try {
      const response = await api.post<{ secret: string; configured: boolean }>(`/integrations/connector-instances/${selectedInstance.id}/webhook-config/regenerate-secret`);
      setSecretNotice("Copy this secret now. It will not be shown again.");
      setWebhookConfig((previous) => ({
        ...previous,
        [selectedInstance.id]: {
          ...(previous[selectedInstance.id] ?? { callConnectUrl: "", cdrUrl: "", active: false, configured: false }),
          configured: response.data?.configured ?? true,
        },
      }));
      void navigator.clipboard.writeText(response.data?.secret ?? "");
    } catch (error) {
      console.error("Failed to regenerate webhook secret", error);
    }
  };

  const handleSaveTrigger = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedInstance) {
      return;
    }

    setSavingTrigger(true);
    try {
      const payload = {
        name: triggerForm.name,
        active: triggerForm.active,
        direction: triggerForm.direction,
        resolveBy: triggerForm.resolveBy,
        entityType: triggerForm.entityType || undefined,
        openAction: triggerForm.openAction,
        displayMode: triggerForm.displayMode,
        route: triggerForm.route || undefined,
        priority: triggerForm.priority,
        triggerKey: triggerForm.triggerKey,
      };

      if (editingTriggerId) {
        await api.put(`/call-settings/connect-triggers/${editingTriggerId}`, payload);
      } else {
        await api.post("/call-settings/connect-triggers", payload);
      }

      const response = await api.get<TriggerRule[]>("/call-settings/connect-triggers");
      setTriggers(response.data ?? []);
      resetTriggerForm();
    } catch (error) {
      console.error("Failed to save trigger rule", error);
    } finally {
      setSavingTrigger(false);
    }
  };

  const handleDeleteTrigger = async (triggerId: string) => {
    try {
      await api.delete(`/call-settings/connect-triggers/${triggerId}`);
      setTriggers((previous) => previous.filter((trigger) => trigger.id !== triggerId));
    } catch (error) {
      console.error("Failed to delete trigger rule", error);
    }
  };

  const handleToggleTrigger = async (triggerId: string, active: boolean) => {
    const trigger = triggers.find((item) => item.id === triggerId);
    if (!trigger) {
      return;
    }

    try {
      await api.put(`/call-settings/connect-triggers/${triggerId}`, {
        name: trigger.name,
        active,
        direction: trigger.direction,
        resolveBy: trigger.resolveBy,
        entityType: trigger.entityType,
        openAction: trigger.openAction,
        displayMode: trigger.displayMode,
        route: undefined,
        priority: trigger.priority,
        triggerKey: trigger.triggerKey,
      });
      setTriggers((previous) => previous.map((item) => item.id === triggerId ? { ...item, active } : item));
    } catch (error) {
      console.error("Failed to update trigger state", error);
    }
  };

  const handleSaveLayout = async (event: React.FormEvent) => {
    event.preventDefault();
    setSavingLayout(true);
    try {
      const payload = {
        layoutName: "Default call layout",
        displayMode: layoutConfig.displayMode,
        showEntityDetails: layoutConfig.showEntityDetails,
        showCallHistory: layoutConfig.showCallHistory,
        showNotes: layoutConfig.showNotes,
        showDisposition: layoutConfig.showDisposition,
        active: layoutConfig.active,
      };
      const response = await api.put<LayoutConfig>("/call-settings/layout-config", payload);
      setLayoutConfig(response.data ?? layoutConfig);
    } catch (error) {
      console.error("Failed to save layout defaults", error);
    } finally {
      setSavingLayout(false);
    }
  };

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading calling settings…</p>;
  }

  if (!canViewCallingSettings) {
    return <p className="text-sm text-muted-foreground">You do not have permission to view calling settings.</p>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Calling admin settings</h1>
        <p className="text-sm text-muted-foreground">Manage provider instances, credentials, webhook endpoints, trigger rules, and call layout defaults for this tenant.</p>
      </div>

      <Tabs defaultValue="providers" className="w-full">
        <TabsList>
          <TabsTrigger value="providers">Calling Provider</TabsTrigger>
          <TabsTrigger value="credentials">Credentials</TabsTrigger>
          <TabsTrigger value="webhooks">Webhooks</TabsTrigger>
          <TabsTrigger value="triggers">Call Opening Rules</TabsTrigger>
          <TabsTrigger value="layout">Call Layout Defaults</TabsTrigger>
        </TabsList>

        <TabsContent value="providers" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle>Provider instance</CardTitle>
              <CardDescription>Create or update a connector instance for the selected calling provider.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" onClick={() => selectInstance(null)}>
                  <Plus className="mr-2 h-4 w-4" /> New instance
                </Button>
                {instances.map((instance) => (
                  <Button key={instance.id} type="button" variant={selectedInstance?.id === instance.id ? "default" : "outline"} onClick={() => selectInstance(instance.id)}>
                    {instance.connectorName}
                  </Button>
                ))}
              </div>

              <form onSubmit={handleSaveInstance} className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="provider">Calling provider</Label>
                    <Select value={providerForm.providerKey} onValueChange={(value) => setProviderForm((previous) => ({ ...previous, providerKey: value }))}>
                      <SelectTrigger id="provider">
                        <SelectValue placeholder="Choose provider" />
                      </SelectTrigger>
                      <SelectContent>
                        {providers.map((provider) => (
                          <SelectItem key={provider.id} value={provider.providerKey}>
                            {provider.displayName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="name">Connector name</Label>
                    <Input id="name" value={providerForm.name} onChange={(event) => setProviderForm((previous) => ({ ...previous, name: event.target.value }))} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="environment">Environment</Label>
                    <Select value={providerForm.environment} onValueChange={(value) => setProviderForm((previous) => ({ ...previous, environment: value }))}>
                      <SelectTrigger id="environment">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SANDBOX">Sandbox</SelectItem>
                        <SelectItem value="PRODUCTION">Production</SelectItem>
                        <SelectItem value="DEVELOPMENT">Development</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="baseUrl">Base URL</Label>
                    <Input id="baseUrl" value={providerForm.baseUrl} onChange={(event) => setProviderForm((previous) => ({ ...previous, baseUrl: event.target.value }))} />
                  </div>
                </div>

                <div className="flex items-center justify-between rounded-md border p-3">
                  <div>
                    <p className="font-medium">Active</p>
                    <p className="text-sm text-muted-foreground">Enable or disable this connector instance.</p>
                  </div>
                  <Switch checked={providerForm.active} onCheckedChange={(value) => setProviderForm((previous) => ({ ...previous, active: value }))} />
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button type="submit" disabled={savingInstance || !canManageCallingSettings}>{savingInstance ? "Saving…" : selectedInstance ? "Update instance" : "Create instance"}</Button>
                  {selectedInstance && (
                    <Button type="button" variant="outline" onClick={() => handleToggleInstance(selectedInstance.id, !selectedInstance.active)} disabled={!canManageCallingSettings}>
                      {selectedInstance.active ? "Deactivate" : "Activate"}
                    </Button>
                  )}
                </div>
              </form>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="credentials" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle>Credentials</CardTitle>
              <CardDescription>Store connector credentials securely. Passwords are never displayed after saving.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {selectedInstance ? (
                <form onSubmit={handleSaveCredentials} className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">{selectedInstance.connectorName}</p>
                      <p className="text-sm text-muted-foreground">{credentialStatus[selectedInstance.id]?.configured ? "Configured" : "Not configured"}</p>
                    </div>
                    <Badge variant={credentialStatus[selectedInstance.id]?.configured ? "default" : "secondary"}>
                      {credentialStatus[selectedInstance.id]?.configured ? "Configured" : "Pending"}
                    </Badge>
                  </div>

                  <div className="grid gap-4 md:grid-cols-2">
                    {credentialFields.map((field) => (
                      <div key={field.key} className="space-y-2">
                        <Label htmlFor={field.key}>{field.label}</Label>
                        <Input id={field.key} type={field.type} value={credentialValues[field.key] ?? ""} onChange={(event) => setCredentialValues((previous) => ({ ...previous, [field.key]: event.target.value }))} />
                      </div>
                    ))}
                  </div>

                  <Button type="submit" disabled={savingCredentials || !canManageCallingSettings}>{savingCredentials ? "Saving…" : "Save credentials"}</Button>
                </form>
              ) : (
                <p className="text-sm text-muted-foreground">Select a connector instance to configure credentials.</p>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="webhooks" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle>Webhook configuration</CardTitle>
              <CardDescription>Review webhook URLs, update verification settings, and rotate secrets without exposing them in normal API responses.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {selectedInstance ? (
                <>
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2 rounded-md border p-3">
                      <Label>Call-connect webhook</Label>
                      <div className="flex items-center gap-2">
                        <Input value={webhookConfig[selectedInstance.id]?.callConnectUrl ?? ""} readOnly />
                        <Button type="button" variant="outline" size="icon" onClick={() => void navigator.clipboard.writeText(webhookConfig[selectedInstance.id]?.callConnectUrl ?? "")}>
                          <Copy className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                    <div className="space-y-2 rounded-md border p-3">
                      <Label>CDR webhook</Label>
                      <div className="flex items-center gap-2">
                        <Input value={webhookConfig[selectedInstance.id]?.cdrUrl ?? ""} readOnly />
                        <Button type="button" variant="outline" size="icon" onClick={() => void navigator.clipboard.writeText(webhookConfig[selectedInstance.id]?.cdrUrl ?? "")}>
                          <Copy className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  </div>

                  <form onSubmit={handleSaveWebhook} className="space-y-4">
                    <div className="grid gap-4 md:grid-cols-2">
                      <div className="space-y-2">
                        <Label htmlFor="webhookName">Webhook name</Label>
                        <Input id="webhookName" value={webhookForm.webhookName} onChange={(event) => setWebhookForm((previous) => ({ ...previous, webhookName: event.target.value }))} />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="targetUrl">Target URL</Label>
                        <Input id="targetUrl" value={webhookForm.targetUrl} onChange={(event) => setWebhookForm((previous) => ({ ...previous, targetUrl: event.target.value }))} />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="verificationMode">Verification mode</Label>
                        <Select value={webhookForm.verificationMode} onValueChange={(value) => setWebhookForm((previous) => ({ ...previous, verificationMode: value }))}>
                          <SelectTrigger id="verificationMode">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="HMAC">HMAC</SelectItem>
                            <SelectItem value="NONE">None</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="webhookActive">Active</Label>
                        <div className="flex items-center gap-2 pt-2">
                          <Switch id="webhookActive" checked={webhookForm.active} onCheckedChange={(value) => setWebhookForm((previous) => ({ ...previous, active: value }))} />
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <Button type="submit" disabled={savingWebhook || !canManageCallingSettings}>{savingWebhook ? "Saving…" : "Save webhook config"}</Button>
                      <Button type="button" variant="outline" onClick={handleRegenerateSecret} disabled={!canManageCallingSettings}>
                        <RefreshCw className="mr-2 h-4 w-4" /> Regenerate secret
                      </Button>
                    </div>
                  </form>

                  {secretNotice ? (
                    <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">{secretNotice}</div>
                  ) : null}
                </>
              ) : (
                <p className="text-sm text-muted-foreground">Select a connector instance to manage webhooks.</p>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="triggers" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle>Call-opening trigger rules</CardTitle>
              <CardDescription>Create and maintain the rules used by the existing call-opening decision engine.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <form onSubmit={handleSaveTrigger} className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="ruleName">Rule name</Label>
                  <Input id="ruleName" value={triggerForm.name} onChange={(event) => setTriggerForm((previous) => ({ ...previous, name: event.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleTriggerKey">Trigger key</Label>
                  <Input id="ruleTriggerKey" value={triggerForm.triggerKey} onChange={(event) => setTriggerForm((previous) => ({ ...previous, triggerKey: event.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleDirection">Direction</Label>
                  <Select value={triggerForm.direction} onValueChange={(value) => setTriggerForm((previous) => ({ ...previous, direction: value }))}>
                    <SelectTrigger id="ruleDirection"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INBOUND">Inbound</SelectItem>
                      <SelectItem value="OUTBOUND">Outbound</SelectItem>
                      <SelectItem value="BOTH">Both</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleResolve">Resolve strategy</Label>
                  <Input id="ruleResolve" value={triggerForm.resolveBy} onChange={(event) => setTriggerForm((previous) => ({ ...previous, resolveBy: event.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleEntity">Target entity type</Label>
                  <Input id="ruleEntity" value={triggerForm.entityType} onChange={(event) => setTriggerForm((previous) => ({ ...previous, entityType: event.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleOpenAction">Open action</Label>
                  <Select value={triggerForm.openAction} onValueChange={(value) => setTriggerForm((previous) => ({ ...previous, openAction: value }))}>
                    <SelectTrigger id="ruleOpenAction"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="OPEN_PAGE">Open page</SelectItem>
                      <SelectItem value="OPEN_CALL_LAYOUT">Open call layout</SelectItem>
                      <SelectItem value="OPEN_MODAL">Open modal</SelectItem>
                      <SelectItem value="OPEN_SIDEBAR">Open sidebar</SelectItem>
                      <SelectItem value="NO_ACTION">No action</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleDisplayMode">Display mode</Label>
                  <Select value={triggerForm.displayMode} onValueChange={(value) => setTriggerForm((previous) => ({ ...previous, displayMode: value }))}>
                    <SelectTrigger id="ruleDisplayMode"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PAGE">Page</SelectItem>
                      <SelectItem value="MODAL">Modal</SelectItem>
                      <SelectItem value="SIDEBAR">Sidebar</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="ruleRoute">Target route</Label>
                  <Input id="ruleRoute" value={triggerForm.route} onChange={(event) => setTriggerForm((previous) => ({ ...previous, route: event.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="rulePriority">Priority</Label>
                  <Input id="rulePriority" type="number" value={triggerForm.priority} onChange={(event) => setTriggerForm((previous) => ({ ...previous, priority: Number(event.target.value) }))} />
                </div>
                <div className="space-y-2 md:col-span-2">
                  <div className="flex items-center justify-between rounded-md border p-3">
                    <div>
                      <p className="font-medium">Active</p>
                      <p className="text-sm text-muted-foreground">Enable or disable this rule.</p>
                    </div>
                    <Switch checked={triggerForm.active} onCheckedChange={(value) => setTriggerForm((previous) => ({ ...previous, active: value }))} />
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <Button type="submit" disabled={savingTrigger || !canManageCallingSettings}>{savingTrigger ? "Saving…" : editingTriggerId ? "Update rule" : "Create rule"}</Button>
                  <Button type="button" variant="outline" onClick={resetTriggerForm} disabled={!canManageCallingSettings}>Reset</Button>
                </div>
              </form>

              <div className="space-y-2">
                {triggers.map((trigger) => (
                  <div key={trigger.id} className="rounded-md border p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <p className="font-medium">{trigger.name}</p>
                        <p className="text-sm text-muted-foreground">{trigger.triggerKey} • {trigger.direction}</p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <Switch checked={trigger.active} onCheckedChange={(value) => void handleToggleTrigger(trigger.id, value)} disabled={!canManageCallingSettings} />
                        <Button type="button" variant="outline" onClick={() => {
                          setEditingTriggerId(trigger.id);
                          setTriggerForm({
                            name: trigger.name,
                            triggerKey: trigger.triggerKey,
                            direction: trigger.direction,
                            resolveBy: trigger.resolveBy,
                            entityType: trigger.entityType ?? "",
                            openAction: trigger.openAction,
                            displayMode: trigger.displayMode,
                            route: "",
                            priority: trigger.priority,
                            active: trigger.active,
                          });
                        }}>Edit</Button>
                        <Button type="button" variant="outline" onClick={() => void handleDeleteTrigger(trigger.id)} disabled={!canManageCallingSettings}>Delete</Button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="layout" className="space-y-4 pt-4">
          <Card>
            <CardHeader>
              <CardTitle>Call layout defaults</CardTitle>
              <CardDescription>Adjust the shared layout defaults for opening call context.</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSaveLayout} className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="layoutDisplayMode">Default display mode</Label>
                    <Select value={layoutConfig.displayMode} onValueChange={(value) => setLayoutConfig((previous) => ({ ...previous, displayMode: value }))}>
                      <SelectTrigger id="layoutDisplayMode"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="PAGE">Page</SelectItem>
                        <SelectItem value="MODAL">Modal</SelectItem>
                        <SelectItem value="SIDEBAR">Sidebar</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="layoutActive">Active</Label>
                    <div className="flex items-center gap-2 pt-2">
                      <Switch id="layoutActive" checked={layoutConfig.active} onCheckedChange={(value) => setLayoutConfig((previous) => ({ ...previous, active: value }))} />
                    </div>
                  </div>
                </div>

                <div className="grid gap-4 md:grid-cols-2">
                  {[
                    { key: "showEntityDetails", label: "Show entity details" },
                    { key: "showCallHistory", label: "Show call history" },
                    { key: "showNotes", label: "Show notes" },
                    { key: "showDisposition", label: "Show disposition section" },
                  ].map((field) => (
                    <div key={field.key} className="flex items-center justify-between rounded-md border p-3">
                      <div>
                        <p className="font-medium">{field.label}</p>
                      </div>
                      <Switch checked={layoutConfig[field.key as keyof LayoutConfig] as boolean} onCheckedChange={(value) => setLayoutConfig((previous) => ({ ...previous, [field.key]: value }))} />
                    </div>
                  ))}
                </div>

                <Button type="submit" disabled={savingLayout || !canManageCallingSettings}>{savingLayout ? "Saving…" : "Save layout defaults"}</Button>
              </form>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
