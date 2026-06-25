// src/app/tenants/page.tsx
"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus, Building2, User, Calendar, BadgeCheck, Pencil } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { tenantApi } from "@/lib/api/tenants";
import { useAuthStore } from "@/lib/store/authStore";
import { format } from "date-fns";
import { TenantProvisionModal } from "./_components/tenant-provision-modal";
import { TenantResponse } from "@/types/tenant";

function TenantsList() {
  const [isModalOpen, setIsModalOpen] = React.useState(false);
  const [selectedTenant, setSelectedTenant] =
  React.useState<TenantResponse | undefined>();

const [modalMode, setModalMode] =
  React.useState<"create" | "edit">("create");
  const userRole = useAuthStore((state) => state.userRole);
  
  const { data: tenants, isLoading, refetch } = useQuery({
    queryKey: ["tenants"],
    queryFn: () => tenantApi.getAllTenants(),
  });

  console.log("Tenats", tenants);

  const getResellerBadge = (tenant: any) => {
    if (!tenant.reseller) {
      return <Badge variant="outline">Direct Tenant</Badge>;
    }
    return (
      <Badge variant="secondary" className="gap-1">
        <User className="h-3 w-3" />
        {tenant.reseller.firstName} {tenant.reseller.lastName}
      </Badge>
    );
  };

  const getStatusBadge = (isActive: boolean) => {
    return isActive ? (
      <Badge variant="default" className="bg-green-500 gap-1">
        <BadgeCheck className="h-3 w-3" />
        Active
      </Badge>
    ) : (
      <Badge variant="destructive">Inactive</Badge>
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900" />
      </div>
    );
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tenants</h1>
          <p className="text-muted-foreground mt-1">
            Manage and monitor all tenant organizations
          </p>
        </div>
        <Button
  onClick={() => {
    setSelectedTenant(undefined);
    setModalMode("create");
    setIsModalOpen(true);
  }}
>
  <Plus className="mr-2 h-4 w-4" />
  Provision Tenant
</Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All Tenants</CardTitle>
          <CardDescription>
            {userRole === "SUPERADMIN" 
              ? "View all tenants across the platform" 
              : "View tenants associated with your reseller account"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {tenants && tenants.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Company</TableHead>
                  <TableHead>Slug</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Max Users</TableHead>
                  <TableHead>Validity</TableHead>
                  {userRole === "SUPERADMIN" && <TableHead>Reseller</TableHead>}
                  <TableHead>Created</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((tenant) => (
                  <TableRow key={tenant.id}>
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <Building2 className="h-4 w-4 text-muted-foreground" />
                        {tenant.name}
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-sm">
                      {tenant.slug}
                    </TableCell>
                    <TableCell>{getStatusBadge(tenant.isActive)}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className="capitalize">
                        {tenant.maxUsers}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className="capitalize">
                        {tenant.subscriptionEndDate
                          ? format(new Date(tenant.subscriptionEndDate), "MMM d, yyyy")
                          : "N/A"}
                      </Badge>

                    </TableCell>
                    {userRole === "SUPERADMIN" && (
                      <TableCell>{getResellerBadge(tenant)}</TableCell>
                    )}
                    <TableCell className="text-muted-foreground">
                      <div className="flex items-center gap-1">
                        <Calendar className="h-3 w-3" />
                        {format(new Date(tenant.createdAt), "MMM d, yyyy")}
                      </div>
                    </TableCell>
                    <TableCell>
  <Button
    size="sm"
    variant="ghost"
    onClick={(e) => {
      e.stopPropagation();
      setSelectedTenant(tenant);
      setModalMode("edit");
      setIsModalOpen(true);
    }}
  >
    <Pencil className="h-4 w-4" />
  </Button>
</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="text-center py-12">
              <Building2 className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-semibold mb-2">No tenants found</h3>
              <p className="text-muted-foreground mb-4">
                {userRole === "RESELLER" 
                  ? "You haven't provisioned any tenants yet" 
                  : "No tenants have been provisioned yet"}
              </p>
              <Button onClick={() => setIsModalOpen(true)} variant="outline">
                <Plus className="mr-2 h-4 w-4" />
                Provision your first tenant
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <TenantProvisionModal
        open={isModalOpen}
  mode={modalMode}
  tenant={selectedTenant}
  onOpenChange={setIsModalOpen}
  onSuccess={() => refetch()}
      />
    </div>
  );
}

export default function TenantsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "tenant", action: "read" }}>
      <TenantsList />
    </ProtectedRoute>
  );
}