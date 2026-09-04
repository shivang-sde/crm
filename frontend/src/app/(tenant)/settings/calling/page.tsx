"use client";

import { useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  KeyRound,
  Loader2,
  PhoneCall,
  RefreshCw,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { toast } from "sonner";

import {
  myCallingSettingsApi,
  type MyAgentMapping,
  type MyCallingConnector,
  type MyCredentialStatus,
} from "@/lib/api/settings";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { SettingsLayout } from "@/components/settings/SettingsLayout";

export default function MyCallingSettingsPage() {
  const [connectors, setConnectors] = useState<MyCallingConnector[]>([]);

  const [selectedConnectorId, setSelectedConnectorId] = useState<string>("");

  const [credentialStatus, setCredentialStatus] = useState<MyCredentialStatus | null>(null);

  const [agentMapping, setAgentMapping] = useState<MyAgentMapping | null>(null);

  const [credentialValues, setCredentialValues] = useState<Record<string, string>>({});

  const [externalAgentId, setExternalAgentId] = useState("");

  const [externalAgentNumber, setExternalAgentNumber] = useState("");

  const [mappingActive, setMappingActive] = useState(true);

  const [loading, setLoading] = useState(true);
  const [loadingDetails, setLoadingDetails] = useState(false);

  const [savingCredentials, setSavingCredentials] = useState(false);

  const [savingMapping, setSavingMapping] = useState(false);

  const selectedConnector = useMemo(
    () =>
      connectors.find(
        (connector) => connector.id === selectedConnectorId
      ) ?? null,
    [connectors, selectedConnectorId]
  );

  useEffect(() => {
    const loadConnectors = async () => {
      try {
        setLoading(true);

        const result = await myCallingSettingsApi.getConnectors();

        setConnectors(result);

        if (result.length > 0) {
          setSelectedConnectorId(result[0].id);
        }
      } catch (error) {
        console.error("Failed to load calling connectors", error);

        toast.error("Unable to load calling providers.");
      } finally {
        setLoading(false);
      }
    };

    void loadConnectors();
  }, []);

  useEffect(() => {
    if (!selectedConnectorId) {
      setCredentialStatus(null);
      setAgentMapping(null);
      return;
    }

    const loadConnectorDetails = async () => {
      try {
        setLoadingDetails(true);

        const [status, mapping] = await Promise.all([
          myCallingSettingsApi.getCredentialStatus(selectedConnectorId),
          myCallingSettingsApi.getAgentMapping(selectedConnectorId),
        ]);

        setCredentialStatus(status);
        setAgentMapping(mapping);

        setExternalAgentId(mapping?.externalAgentId ?? "");

        setExternalAgentNumber(mapping?.externalAgentNumber ?? "");

        setMappingActive(mapping?.active ?? true);

        setCredentialValues({});
      } catch (error) {
        console.error("Failed to load calling configuration", error);

        toast.error("Unable to load your calling configuration.");
      } finally {
        setLoadingDetails(false);
      }
    };

    void loadConnectorDetails();
  }, [selectedConnectorId]);

  const saveCredentials = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!selectedConnector) {
      return;
    }

    const missingField = selectedConnector.credentialFields.find(
      (field) => field.required && !credentialValues[field.key]?.trim()
    );

    if (missingField) {
      toast.error(`${missingField.label} is required.`);
      return;
    }

    try {
      setSavingCredentials(true);

      const status = await myCallingSettingsApi.saveCredentials(
        selectedConnector.id,
        {
          authType: "PROVIDER_SPECIFIC",
          values: Object.fromEntries(
            selectedConnector.credentialFields.map((field) => [
              field.key,
              credentialValues[field.key]?.trim() ?? "",
            ])
          ),
        }
      );

      setCredentialStatus(status);
      setCredentialValues({});

      toast.success("Your calling credentials were saved.");
    } catch (error) {
      console.error("Failed to save calling credentials", error);

      toast.error("Unable to save calling credentials.");
    } finally {
      setSavingCredentials(false);
    }
  };

  const clearCredentials = async () => {
    if (!selectedConnector) {
      return;
    }

    try {
      setSavingCredentials(true);

      await myCallingSettingsApi.deleteCredentials(selectedConnector.id);

      setCredentialStatus({
        connectorInstanceId: selectedConnector.id,
        configured: false,
        authType: "PROVIDER_SPECIFIC",
      });

      setCredentialValues({});

      toast.success("Your calling credentials were removed.");
    } catch (error) {
      console.error("Failed to remove credentials", error);

      toast.error("Unable to remove calling credentials.");
    } finally {
      setSavingCredentials(false);
    }
  };

  const saveAgentMapping = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!selectedConnector) {
      return;
    }

    if (!externalAgentId.trim() && !externalAgentNumber.trim()) {
      toast.error("Enter an agent ID or agent number.");
      return;
    }

    try {
      setSavingMapping(true);

      const saved = await myCallingSettingsApi.saveAgentMapping(
        selectedConnector.id,
        {
          externalAgentId: externalAgentId.trim() || null,
          externalAgentNumber: externalAgentNumber.trim() || null,
          active: mappingActive,
        }
      );

      setAgentMapping(saved);

      toast.success("Your provider agent mapping was saved.");
    } catch (error) {
      console.error("Failed to save agent mapping", error);

      toast.error("Unable to save provider agent mapping.");
    } finally {
      setSavingMapping(false);
    }
  };

  if (loading) {
    return (
      <SettingsLayout>
        <div className="flex min-h-[320px] items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      </SettingsLayout>
    );
  }

  if (connectors.length === 0) {
    return (
      <SettingsLayout>
        <Card>
          <CardHeader>
            <CardTitle>Calling is not available</CardTitle>
            <CardDescription>
              Your administrator has not enabled an active calling provider for this tenant.
            </CardDescription>
          </CardHeader>
        </Card>
      </SettingsLayout>
    );
  }

  return (
    <SettingsLayout>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">My calling settings</h1>

            <p className="mt-1 text-sm text-muted-foreground">
              Configure your personal calling credentials and provider agent identity.
            </p>
          </div>

          <div className="w-full md:w-[320px]">
            <Label htmlFor="callingConnector">Calling provider</Label>

            <Select value={selectedConnectorId} onValueChange={setSelectedConnectorId}>
              <SelectTrigger id="callingConnector" className="mt-2">
                <SelectValue placeholder="Choose provider" />
              </SelectTrigger>

              <SelectContent>
                {connectors.map((connector) => (
                  <SelectItem key={connector.id} value={connector.id}>
                    {connector.connectorName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {selectedConnector ? (
          <Card>
            <CardContent className="flex flex-col gap-4 pt-6 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-3">
                <div className="rounded-lg bg-muted p-2">
                  <PhoneCall className="h-5 w-5" />
                </div>

                <div>
                  <p className="font-medium">{selectedConnector.connectorName}</p>

                  <p className="text-sm text-muted-foreground">
                    {selectedConnector.providerName}
                    {selectedConnector.environment ? ` • ${selectedConnector.environment}` : ""}
                  </p>
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                <Badge variant={credentialStatus?.configured ? "default" : "secondary"}>
                  {credentialStatus?.configured ? "Credentials configured" : "Credentials required"}
                </Badge>

                <Badge variant={agentMapping?.active ? "default" : "secondary"}>
                  {agentMapping?.active ? "Agent mapped" : "Agent mapping required"}
                </Badge>
              </div>
            </CardContent>
          </Card>
        ) : null}

        {loadingDetails ? (
          <div className="flex min-h-[220px] items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <Tabs defaultValue="credentials" className="w-full">
            <TabsList>
              <TabsTrigger value="credentials">Credentials</TabsTrigger>

              <TabsTrigger value="agent">Agent identity</TabsTrigger>

              <TabsTrigger value="status">Status</TabsTrigger>
            </TabsList>

            <TabsContent value="credentials" className="pt-4">
              <Card>
                <CardHeader>
                  <div className="flex items-start gap-3">
                    <KeyRound className="mt-1 h-5 w-5" />

                    <div>
                      <CardTitle>Provider credentials</CardTitle>

                      <CardDescription>
                        These credentials are used only when you initiate provider calls.
                        Passwords are never returned after saving.
                      </CardDescription>
                    </div>
                  </div>
                </CardHeader>

                <CardContent>
                  <form onSubmit={saveCredentials} className="space-y-5">
                    <div className="grid gap-4 md:grid-cols-2">
                      {selectedConnector?.credentialFields.map((field) => (
                        <div key={field.key} className="space-y-2">
                          <Label htmlFor={field.key}>
                            {field.label} {field.required ? " *" : ""}
                          </Label>

                          <Input
                            id={field.key}
                            type={field.type}
                            autoComplete={field.type === "password" ? "new-password" : "off"}
                            value={credentialValues[field.key] ?? ""}
                            placeholder={
                              credentialStatus?.configured
                                ? "Enter a new value to replace"
                                : `Enter ${field.label.toLowerCase()}`
                            }
                            onChange={(event) =>
                              setCredentialValues((previous) => ({
                                ...previous,
                                [field.key]: event.target.value,
                              }))
                            }
                          />
                        </div>
                      ))}
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <Button type="submit" disabled={savingCredentials}>
                        {savingCredentials ? (
                          <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            Saving…
                          </>
                        ) : credentialStatus?.configured ? (
                          "Replace credentials"
                        ) : (
                          "Save credentials"
                        )}
                      </Button>

                      {credentialStatus?.configured ? (
                        <Button
                          type="button"
                          variant="outline"
                          disabled={savingCredentials}
                          onClick={() => void clearCredentials()}
                        >
                          Remove credentials
                        </Button>
                      ) : null}
                    </div>
                  </form>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="agent" className="pt-4">
              <Card>
                <CardHeader>
                  <div className="flex items-start gap-3">
                    <UserRound className="mt-1 h-5 w-5" />

                    <div>
                      <CardTitle>Provider agent identity</CardTitle>

                      <CardDescription>
                        Incoming calls use this mapping to send the active-call workspace
                        only to your CRM account.
                      </CardDescription>
                    </div>
                  </div>
                </CardHeader>

                <CardContent>
                  <form onSubmit={saveAgentMapping} className="space-y-5">
                    <div className="grid gap-4 md:grid-cols-2">
                      <div className="space-y-2">
                        <Label htmlFor="externalAgentId">Provider agent ID</Label>

                        <Input
                          id="externalAgentId"
                          value={externalAgentId}
                          placeholder="Example: udit8755"
                          onChange={(event) => setExternalAgentId(event.target.value)}
                        />
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="externalAgentNumber">Provider agent number</Label>

                        <Input
                          id="externalAgentNumber"
                          value={externalAgentNumber}
                          placeholder="Example: 24006"
                          onChange={(event) => setExternalAgentNumber(event.target.value)}
                        />
                      </div>
                    </div>

                    <div className="flex items-center justify-between rounded-lg border p-4">
                      <div>
                        <p className="font-medium">Active mapping</p>

                        <p className="text-sm text-muted-foreground">
                          Receive incoming call-opening events assigned to this provider
                          identity.
                        </p>
                      </div>

                      <Switch checked={mappingActive} onCheckedChange={setMappingActive} />
                    </div>

                    <Button type="submit" disabled={savingMapping}>
                      {savingMapping ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Saving…
                        </>
                      ) : (
                        "Save agent identity"
                      )}
                    </Button>
                  </form>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="status" className="pt-4">
              <div className="grid gap-4 md:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <ShieldCheck className="h-5 w-5" />
                      Outgoing calls
                    </CardTitle>
                  </CardHeader>

                  <CardContent>
                    {credentialStatus?.configured ? (
                      <div className="flex items-start gap-3">
                        <CheckCircle2 className="mt-0.5 h-5 w-5 text-green-600" />

                        <div>
                          <p className="font-medium">Ready</p>

                          <p className="text-sm text-muted-foreground">
                            Click-to-call will use your personal provider credential.
                          </p>
                        </div>
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        Configure credentials before using click-to-call.
                      </p>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <RefreshCw className="h-5 w-5" />
                      Incoming calls
                    </CardTitle>
                  </CardHeader>

                  <CardContent>
                    {agentMapping?.active ? (
                      <div className="flex items-start gap-3">
                        <CheckCircle2 className="mt-0.5 h-5 w-5 text-green-600" />

                        <div>
                          <p className="font-medium">Ready</p>

                          <p className="text-sm text-muted-foreground">
                            Incoming call events can be targeted to your account.
                          </p>
                        </div>
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground">
                        Configure and activate your provider agent identity.
                      </p>
                    )}
                  </CardContent>
                </Card>
              </div>
            </TabsContent>
          </Tabs>
        )}
      </div>
    </SettingsLayout>
  );
}