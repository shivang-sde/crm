"use client";

import React from "react";
import { usePathname, useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { roleApi } from "@/lib/api/roles";
import { useAuthStore } from "@/lib/store/authStore";
import { getNavigationItems } from "@/lib/constants/navigation";
import { Footer } from "./Footer";
import { Header, type BreadcrumbItem } from "./Header";
import { Sidebar } from "./Sidebar";
import { CallOpeningProvider } from "@/components/call-opening/CallOpeningProvider";

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
  const queryClient = useQueryClient();

  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const tenant = useAuthStore((state) => state.tenant);
  const userRole = useAuthStore((state) => state.userRole);
  const logout = useAuthStore((state) => state.logout);
  const setAuth = useAuthStore((state) => state.setAuth);
  const setPermissions = useAuthStore((state) => state.setPermissions);
  const permissions = useAuthStore((state) => state.permissions);
  const hydrated = useAuthStore((state) => state.hydrated);

  // Layout View Controls
  const [bootstrapComplete, setBootstrapComplete] = React.useState(false);
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const [desktopCollapsed, setDesktopCollapsed] = React.useState(false);

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
      } catch {
        console.error("Failed to load permissions");

        if (userRole === "RESELLER") {
          const defaultPerms = new Map<string, string>();
          defaultPerms.set("tenant:read", "ALL");
          defaultPerms.set("tenant:write", "ALL");
          defaultPerms.set("tenant:delete", "ALL");
          defaultPerms.set("user:read", "ALL");
          defaultPerms.set("user:write", "ALL");
          defaultPerms.set("user:delete", "ALL");
          defaultPerms.set("report:read", "ALL");
          defaultPerms.set("report:export", "ALL");

          setPermissions(defaultPerms);
        }
      }
    };

    const initializeAuth = async () => {
      if (user && accessToken) {
        if (user.roleId && !useAuthStore.getState().permissionsLoaded) {
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
      } catch {
        logout();
        queryClient.clear();
        router.replace("/sign-in");
      } finally {
        setBootstrapComplete(true);
      }
    };

    initializeAuth();
  }, [hydrated, bootstrapComplete, user, accessToken, setAuth, setPermissions, logout, router, userRole, queryClient]);

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
      queryClient.clear();
      toast.success("Logged out successfully");
      router.replace("/sign-in");
    },
    onError: () => {
      logout();
      queryClient.clear();
      router.replace("/sign-in");
    },
  });

  const breadcrumbs = React.useMemo<BreadcrumbItem[]>(() => {
    const segments = pathname.split("/").filter(Boolean);
    return segments.map((segment, index) => {
      const url = `/${segments.slice(0, index + 1).join("/")}`;
      return {
        label: formatBreadcrumbLabel(segment),
        href: url,
      };
    });
  }, [pathname]);

  // FIX: Properly passes parameters to match your defined signature types cleanly
  const navigationItems = React.useMemo(() => {
    return getNavigationItems(userRole, permissions || undefined);
  }, [userRole, permissions]);

  const fallbackUserName = React.useMemo(() => {
    if (!user) return "User";
    const record = user as any;
    return record.name || record.firstName || record.username || record.email || "Active User";
  }, [user]);

  if (!hydrated || !bootstrapComplete) {
    return (
      <div className="flex h-screen w-screen flex-col items-center justify-center bg-slate-50 gap-2">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
        <p className="text-sm font-medium text-slate-500">Loading Workspace...</p>
      </div>
    );
  }

  return (
    <CallOpeningProvider>
      <div className="flex min-h-screen w-full bg-slate-50/50">
        <Sidebar
          tenantName={tenant?.name}
          roleLabel={userRole || undefined}
          navigationItems={navigationItems}
          activePathname={pathname}
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          isCollapsed={desktopCollapsed}
          setIsCollapsed={(collapsed) => setDesktopCollapsed(collapsed)}
          user={user ? { name: fallbackUserName, email: user.email || "" } : null}
          onLogout={() => logoutMutation.mutate()}
          isLoggingOut={logoutMutation.isPending}
        />

        <div className="flex flex-1 flex-col min-w-0 overflow-hidden">
          <header className="sticky top-0 z-20 flex h-16 shrink-0 items-center border-b border-slate-200 bg-white shadow-sm">
            <Header 
              userName={fallbackUserName}
              roleName={userRole || undefined}
              onLogout={() => logoutMutation.mutate()}
              logoutPending={logoutMutation.isPending}
              onToggleSidebar={() => setSidebarOpen((prev) => !prev)}
              breadcrumbs={breadcrumbs}
            />
          </header>

          <main className="flex-1 p-2 md:p-4 lg:p-6 max-w-8xl w-full mx-auto">
            {children}
          </main>

          <Footer />
        </div>
      </div>
    </CallOpeningProvider>
  );
}
