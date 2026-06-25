"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Search, Shield, Users, Edit, Trash2 } from "lucide-react";

import { roleApi } from "@/lib/api/roles";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Role } from "@/types/rbac";

function RoleList() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null);

  const { data: roles, isLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => roleApi.deleteRole(id),
    onSuccess: () => {
      toast.success("Role deleted successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      setRoleToDelete(null);
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to delete role");
      setRoleToDelete(null);
    },
  });

  const filteredRoles = roles?.filter(role => 
    role.name.toLowerCase().includes(search.toLowerCase()) || 
    (role.description && role.description.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold tracking-tight">Roles</h1>
        <Link href="/roles/create">
          <Button>
            <Plus className="mr-2 h-4 w-4" />
            Create Role
          </Button>
        </Link>
      </div>

      <div className="flex bg-white p-4 rounded-lg border">
        <div className="relative w-full max-w-md">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-gray-500" />
          <Input
            placeholder="Search roles..."
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map(i => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="h-24 bg-gray-100"></CardHeader>
              <CardContent className="h-16"></CardContent>
            </Card>
          ))}
        </div>
      ) : filteredRoles?.length === 0 ? (
        <div className="bg-white border rounded-lg p-12 text-center text-gray-500">
          No roles found. Try adjusting your search.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredRoles?.map((role) => (
            <Card key={role.id} className="flex flex-col hover:border-blue-300 transition-colors">
              <CardHeader className="pb-3">
                <div className="flex justify-between items-start">
                  <CardTitle className="flex items-center gap-2 text-lg">
                    <Shield className={`w-5 h-5 ${role.level === 'PLATFORM' ? 'text-red-500' : 'text-blue-500'}`} />
                    {role.name}
                  </CardTitle>
                  {role.isDefault && (
                    <Badge variant="secondary" className="bg-gray-100">Default</Badge>
                  )}
                </div>
                <CardDescription className="line-clamp-2 mt-1">
                  {role.description || "No description provided."}
                </CardDescription>
              </CardHeader>
              <CardContent className="pb-2 flex-1">
                <div className="flex items-center text-sm text-gray-500 gap-1 mt-2">
                  <Users className="w-4 h-4" />
                  {role.userCount || 0} Users assigned
                </div>
              </CardContent>
              <CardFooter className="flex justify-end gap-2 pt-4 border-t mt-auto">
                <Link href={`/roles/${role.id}`}>
                  <Button variant="outline" size="sm">
                    <Edit className="w-4 h-4 mr-2" />
                    Manage
                  </Button>
                </Link>
                {!role.isDefault && (
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    className="text-red-600 hover:text-red-700 hover:bg-red-50"
                    onClick={() => setRoleToDelete(role)}
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                )}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      <AlertDialog open={!!roleToDelete} onOpenChange={() => setRoleToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Role</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete the role "{roleToDelete?.name}"? 
              This action cannot be undone. Users assigned to this role might lose access.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-red-600 hover:bg-red-700"
              onClick={() => roleToDelete && deleteMutation.mutate(roleToDelete.id)}
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete Role"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

export default function RolesPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'role_manage' }}>
      <RoleList />
    </ProtectedRoute>
  );
}
