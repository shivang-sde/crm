"use client";

import React from "react";
import { usePathname, useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { roleApi } from "@/lib/api/roles";
import { useAuthStore } from "@/lib/store/authStore";
import { getNavigationItems } from "@/lib/constants/navigation";
import { Footer } from "./Footer";
import { Header, type BreadcrumbItem } from "./Header";
import { Sidebar } from "./Sidebar";

interface AppLayoutProps {
  children: React.ReactNode;
}

function formatBreadcrumbLabel(segment: string) {
  return segment
    .replace(/-/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

export default function AppLayout({ children }: AppLayoutProps) {
  const router = useRouter();
  const pathname = usePathname();

  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const tenant = useAuthStore((state) => state.tenant);
  const userRole = useAuthStore((state) => state.userRole);
  const logout = useAuthStore((state) => state.logout);
  const setAuth = useAuthStore((state) => state.setAuth);
  const setPermissions = useAuthStore((state) => state.setPermissions);
  const permissions = useAuthStore((state) => state.permissions);
  const hydrated = useAuthStore((state) => state.hydrated);

  const [bootstrapComplete, setBootstrapComplete] = React.useState(false);
  const [sidebarOpen, setSidebarOpen] = React.useState(false);

  React.useEffect(() => {
    if (!hydrated || bootstrapComplete) {
      return;
    }

    const loadPermissions = async (roleId?: string) => {
      try {
        if (!roleId) {
          setPermissions(new Map());
          return;
        }

        const role = await roleApi.getRole(roleId);
        const permMap = new Map<string, string>();

        if (role.permissions) {
          role.permissions.forEach((permission) => {
            permMap.set(`${permission.module}:${permission.action}`, permission.accessScope);
          });
        }

        setPermissions(permMap);
      } catch (error) {
        console.error("Failed to load permissions", error);
    
    // Fallback: Set default permissions based on role name
    if (userRole === 'RESELLER') {
      const defaultPerms = new Map<string, string>();
      // Tenant permissions
      defaultPerms.set('tenant:read', 'ALL');
      defaultPerms.set('tenant:write', 'ALL');
      defaultPerms.set('tenant:delete', 'ALL');
      // User permissions  
      defaultPerms.set('user:read', 'ALL');
      defaultPerms.set('user:write', 'ALL');
      defaultPerms.set('user:delete', 'ALL');
      // Report permissions
      defaultPerms.set('report:read', 'ALL');
      defaultPerms.set('report:export', 'ALL');
      
      setPermissions(defaultPerms);
    } else if (userRole === 'SUPERADMIN') {
      // SUPERADMIN should have all permissions, but since we can't load,
      // we'll set a flag or handle differently
      console.warn('SUPERADMIN permissions failed to load');
    }
      } 
    };

    const initializeAuth = async () => {
      if (user && accessToken) {
        if (user.roleId && useAuthStore.getState().permissions.size === 0) {
          await loadPermissions(user.roleId);
        }
        setBootstrapComplete(true);
        return;
      }

      try {
        const auth = await authApi.refreshAuth();
        setAuth(auth.user, auth.accessToken, auth.user.roleName || auth.user.role || "EMPLOYEE", auth.tenant);

        if (auth.user.roleId) {
          await loadPermissions(auth.user.roleId);
        }
      } catch (error) {
        logout();
        router.replace("/sign-in");
      } finally {
        setBootstrapComplete(true);
      }
    };

    initializeAuth();
  }, [hydrated, bootstrapComplete, user, accessToken, setAuth, setPermissions, logout, router]);

  React.useEffect(() => {
    if (!hydrated || !bootstrapComplete) {
      return;
    }

    if (!user) {
      router.replace("/sign-in");
    }
  }, [hydrated, bootstrapComplete, user, router]);

  const logoutMutation = useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      logout();
      toast.success("Logged out successfully");
      router.replace("/sign-in");
    },
    onError: () => {
      logout();
      router.replace("/sign-in");
    },
  });

  const navigationItems = React.useMemo(
    () => getNavigationItems(userRole, permissions),
    [userRole, permissions]
  );

  const breadcrumbs: BreadcrumbItem[] = React.useMemo(() => {
    const pathSegments = pathname?.split("/").filter(Boolean) ?? [];
    return pathSegments.map((segment, index) => ({
      label: formatBreadcrumbLabel(segment),
      href: `/${pathSegments.slice(0, index + 1).join("/")}`,
    }));
  }, [pathname]);

  const userName = `${user?.firstName ?? ""} ${user?.lastName ?? ""}`.trim();
  const roleLabel = user?.roleName ?? userRole?.toLowerCase().replace(/_/g, " ");

  if (!hydrated || !bootstrapComplete) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <div className="relative min-h-screen md:flex">
        <Sidebar
          tenantName={tenant?.name}
          roleLabel={roleLabel ?? undefined}
          navigationItems={navigationItems}
          activePathname={pathname}
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />

        <div className="flex flex-1 flex-col md:pl-0">
          <Header
            userName={userName}
            roleName={roleLabel ?? undefined}
            onLogout={() => logoutMutation.mutate()}
            logoutPending={logoutMutation.isPending}
            onToggleSidebar={() => setSidebarOpen((prev) => !prev)}
            breadcrumbs={breadcrumbs}
          />
          <main className="flex-1 overflow-auto bg-gray-50">
            <div className="mx-auto w-full max-w-screen-2xl px-4 py-6 sm:px-6">
              {children}
            </div>
          </main>

          <Footer />
        </div>
      </div>
    </div>
  );
}
