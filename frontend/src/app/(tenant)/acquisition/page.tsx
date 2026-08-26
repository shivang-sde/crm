"use client";

import { useState } from "react";
import Link from "next/link";
import { Pencil, Plus, Power, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  useAcquisitionConfigs,
  useCreateAcquisitionConfig,
  useDeleteAcquisitionConfig,
  useUpdateAcquisitionConfig,
} from "@/lib/hooks/acquisition";
import { AcquisitionConfigDialog } from "@/components/acquisition/AcquisitionConfigDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  LeadIngestionConfigResponse,
  LeadIngestionConfigCreateRequest,
  LeadIngestionTransportType,
} from "@/types/acquisition";

const transportLabels: Record<LeadIngestionTransportType, string> = {
  WEBHOOK: "Webhook",
  API: "API",
  FORM: "Form",
  CONNECTOR: "Connector",
  POLLING: "Polling",
  IMPORT: "Import",
};

export default function AcquisitionPage() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<LeadIngestionConfigResponse | null>(null);

  const { canViewAcquisition, canEditAcquisition, canDeleteAcquisition } =
    usePermissions();

  const { data, isLoading } = useAcquisitionConfigs();
  const createConfig = useCreateAcquisitionConfig();
  const updateConfig = useUpdateAcquisitionConfig();
  const deleteConfig = useDeleteAcquisitionConfig();

  const configs = data?.data ?? [];

  if (!canViewAcquisition) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Lead Acquisition</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view acquisition configurations.
        </p>
      </div>
    );
  }

  const handleSave = async (values: LeadIngestionConfigCreateRequest) => {
    try {
      if (editing?.id) {
        await updateConfig.mutateAsync({ id: editing.id, data: values });
        toast.success("Configuration updated successfully");
      } else {
        await createConfig.mutateAsync(values);
        toast.success("Configuration created successfully");
      }
      setOpen(false);
      setEditing(null);
    } catch {
      toast.error("Failed to save configuration");
    }
  };

  const handleToggle = async (config: LeadIngestionConfigResponse) => {
    try {
      await updateConfig.mutateAsync({
        id: config.id,
        data: {
          name: config.name,
          transportType: config.transportType,
          active: !config.active,
          settings: config.settings ?? {},
        },
      });
      toast.success(config.active ? "Configuration deactivated" : "Configuration activated");
    } catch {
      toast.error("Failed to update configuration");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteConfig.mutateAsync(id);
      toast.success("Configuration deleted successfully");
    } catch {
      toast.error("Failed to delete configuration");
    }
  };

  const isSubmitting = createConfig.isPending || updateConfig.isPending;

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Lead Acquisition</h1>
          <p className="text-sm text-muted-foreground">
            Define how lead data enters the CRM from external sources.
          </p>
        </div>

        {canEditAcquisition && (
          <Button
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            <Plus className="mr-2 h-4 w-4" /> Add Configuration
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Configurations</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading configurations…</p>
          ) : configs.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No acquisition configurations yet. Create your first configuration to
              start receiving Leads.
            </p>
          ) : (
            <div className="space-y-3">
              {configs.map((config) => (
                <div
                  key={config.id}
                  className="flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-medium">{config.name}</h3>
                      <Badge variant={config.active ? "default" : "secondary"}>
                        {config.active ? "Active" : "Inactive"}
                      </Badge>
                      <Badge variant="outline">
                        {transportLabels[config.transportType] ?? config.transportType}
                      </Badge>
                    </div>
                    {config.publicKey && (
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        Public key: {config.publicKey}
                      </p>
                    )}
                  </div>

                  {(canViewAcquisition || canEditAcquisition || canDeleteAcquisition) && (
                    <div className="flex flex-wrap items-center gap-2">
                      <Link href={`/acquisition/configs/${config.id}/mappings`}>
                        <Button variant="outline" size="sm">
                          Mappings
                        </Button>
                      </Link>
                      <Link href={`/acquisition/configs/${config.id}/events`}>
                        <Button variant="outline" size="sm">
                          Events
                        </Button>
                      </Link>
                      {canEditAcquisition && (
                        <>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setEditing(config);
                              setOpen(true);
                            }}
                          >
                            <Pencil className="mr-2 h-4 w-4" /> Edit
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => handleToggle(config)}>
                            <Power className="mr-2 h-4 w-4" />
                            {config.active ? "Deactivate" : "Activate"}
                          </Button>
                        </>
                      )}
                      {canDeleteAcquisition && (
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => handleDelete(config.id)}
                        >
                          <Trash2 className="mr-2 h-4 w-4" /> Delete
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AcquisitionConfigDialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setEditing(null);
        }}
        editing={editing}
        onSubmit={handleSave}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
