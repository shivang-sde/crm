"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Search, MoreHorizontal, Check, X, Shield, ShieldAlert, Edit, Trash2 } from "lucide-react";

import { userApi } from "@/lib/api/users";
import { roleApi } from "@/lib/api/roles";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { UserFormModal } from "@/components/users/UserFormModal";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { User } from "@/types/rbac";

function UserList() {
  const queryClient = useQueryClient();
  const { hasPermission } = usePermissions();
  // Mirrors the backend's dual user-management paths: tenant context requires
  // admin:user_manage; platform context uses user:write / user:delete.
  const canUpdateUsers =
    hasPermission("admin", "user_manage") || hasPermission("user", "write");
  const canDeleteUsers =
    hasPermission("admin", "user_manage") || hasPermission("user", "delete");
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [roleId, setRoleId] = useState<string>("all");
  const [isActive, setIsActive] = useState<string>("all");

  const [userToDelete, setUserToDelete] = useState<User | null>(null);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingUserId, setEditingUserId] = useState<string | null>(null);

  React.useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 500);
    return () => clearTimeout(timer);
  }, [search]);

  const { data: roles } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const { data: usersData, isLoading } = useQuery({
    queryKey: ["users", page, debouncedSearch, roleId, isActive],
    queryFn: () =>
      userApi.getUsers({
        page,
        search: debouncedSearch || undefined,
        roleId: roleId !== "all" ? roleId : undefined,
        isActive: isActive !== "all" ? isActive === "true" : undefined,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => userApi.deleteUser(id),
    onSuccess: () => {
      toast.success("User deleted successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setUserToDelete(null);
    },
    onError: () => {
      toast.error("Failed to delete user");
    },
  });

  const activateMutation = useMutation({
    mutationFn: (id: string) => userApi.activateUser(id),
    onSuccess: () => {
      toast.success("User activated successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: () => toast.error("Failed to activate user"),
  });

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => userApi.deactivateUser(id),
    onSuccess: () => {
      toast.success("User deactivated successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: () => toast.error("Failed to deactivate user"),
  });

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold tracking-tight">Users</h1>
        {canUpdateUsers && (
          <Button onClick={() => setIsCreateModalOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Create User
          </Button>
        )}
      </div>

      <div className="flex flex-col sm:flex-row gap-4 items-center justify-between bg-white p-4 rounded-lg border">
        <div className="relative w-full sm:w-96">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-gray-500" />
          <Input
            placeholder="Search users..."
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex gap-4 w-full sm:w-auto">
          <Select value={roleId} onValueChange={setRoleId}>
            <SelectTrigger className="w-full sm:w-[180px]">
              <SelectValue placeholder="All Roles" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Roles</SelectItem>
              {roles?.map((role) => (
                <SelectItem key={role.id} value={role.id}>
                  {role.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={isActive} onValueChange={setIsActive}>
            <SelectTrigger className="w-full sm:w-[150px]">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="true">Active</SelectItem>
              <SelectItem value="false">Inactive</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="bg-white rounded-lg border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Last Login</TableHead>
              <TableHead className="w-[80px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : usersData?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  No users found.
                </TableCell>
              </TableRow>
            ) : (
              usersData?.content.map((u) => (
                <TableRow key={u.id}>
                  <TableCell className="font-medium">
                    {u.firstName} {u.lastName}
                  </TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className="flex w-fit items-center gap-1">
                      {u.roleName === 'Superadmin' ? (
                        <ShieldAlert className="w-3 h-3 text-red-500" />
                      ) : (
                        <Shield className="w-3 h-3 text-blue-500" />
                      )}
                      {u.roleName || "No Role"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {u.isActive ? (
                      <Badge variant="secondary" className="bg-green-100 text-green-800 hover:bg-green-100">Active</Badge>
                    ) : (
                      <Badge variant="secondary" className="bg-gray-100 text-gray-800 hover:bg-gray-100">Inactive</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-gray-500 text-sm">
                    {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString() : "Never"}
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="h-8 w-8 p-0">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {canUpdateUsers && (
                          <DropdownMenuItem
                            className="cursor-pointer"
                            onClick={() => setEditingUserId(u.id)}
                          >
                            <Edit className="mr-2 h-4 w-4" />
                            Edit User
                          </DropdownMenuItem>
                        )}
                        {canUpdateUsers && u.isActive && (
                          <DropdownMenuItem
                            className="cursor-pointer"
                            onClick={() => deactivateMutation.mutate(u.id)}
                          >
                            <X className="mr-2 h-4 w-4" />
                            Deactivate
                          </DropdownMenuItem>
                        )}
                        {canUpdateUsers && !u.isActive && (
                          <DropdownMenuItem
                            className="cursor-pointer"
                            onClick={() => activateMutation.mutate(u.id)}
                          >
                            <Check className="mr-2 h-4 w-4" />
                            Activate
                          </DropdownMenuItem>
                        )}
                        {canDeleteUsers && (
                          <DropdownMenuItem
                            className="cursor-pointer text-red-600 focus:text-red-600"
                            onClick={() => setUserToDelete(u)}
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Delete
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        {usersData && usersData.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <div className="text-sm text-gray-500">
              Showing page {page + 1} of {usersData.totalPages}
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= usersData.totalPages - 1}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Create User Modal */}
      <UserFormModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        mode="create"
        onSuccess={() => {
          setPage(0);
          setSearch("");
        }}
      />

      {/* Edit User Modal */}
      {editingUserId && (
        <UserFormModal
          isOpen={!!editingUserId}
          onClose={() => setEditingUserId(null)}
          mode="edit"
          userId={editingUserId}
        />
      )}

      <AlertDialog open={!!userToDelete} onOpenChange={() => setUserToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. This will permanently delete the user
              account for {userToDelete?.firstName} {userToDelete?.lastName} and remove their data from our servers.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-red-600 hover:bg-red-700"
              onClick={() => userToDelete && deleteMutation.mutate(userToDelete.id)}
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete User"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

export default function UsersPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'user_manage' }}>
      <UserList />
    </ProtectedRoute>
  );
}
