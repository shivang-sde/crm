import {
  Home,
  Building,
  Shield,
  Users,
  Target,
  Phone,
  Calendar,
  CheckSquare,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface NavItem {
  name: string;
  href: string;
  icon: LucideIcon;
  show: boolean;
}

const dashboardRoutes: Record<string, string> = {
  SUPERADMIN: "/superadmin",
  RESELLER: "/reseller",
  ADMIN: "/admin/dashboard",
  MANAGER: "/dashboard",
  EMPLOYEE: "/dashboard",
};

const roleRoutePrefixes: Record<string, string[]> = {
  SUPERADMIN: ["/users", "/roles", "/tenants", "/superadmin", "/reseller", "/admin"],
  RESELLER: ["/tenants", "/reseller",],
  ADMIN: ["/users", "/roles", "/admin/dashboard", "/admin/settings", "/leads", "/accounts", "/contacts", "/deals", "/tasks", "/calls", "/meetings"],
  MANAGER: ["/dashboard", "/leads", "/accounts", "/contacts", "/deals", "/tasks", "/calls", "/meetings"],
  EMPLOYEE: ["/dashboard", "/leads", "/accounts", "/contacts", "/deals", "/tasks", "/calls", "/meetings"],
};

const publicRoutes = ["/sign-in", "/sign-up", "/forgot-password", "/reset-password", "/"];

export function getDashboardRoute(role: string | null): string {
  console.log("Determining dashboard route for role:", role);
  if (!role) return "/home";
  return dashboardRoutes[role] || "/home";
}

export function isPublicRoute(pathname: string): boolean {
  console.log("Checking if route is public:", pathname);
  return publicRoutes.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

export function isRouteAllowedForRole(pathname: string, role: string | null): boolean {
  console.log("Checking if route is allowed for role:", pathname, role);
  if (!role) {
    return false;
  }

  if (pathname === "/") {
    return true;
  }

  const allowedPrefixes = roleRoutePrefixes[role] || [];
  return allowedPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/**
 * Returns navigation items based on the current role.
 * Roles: SUPERADMIN, RESELLER, ADMIN, EMPLOYEE
 */
export function getNavigationItems(role: string | null, permissions?: Map<string, string>): NavItem[] {
   console.log("Navigation role:", role);
  const baseItems: NavItem[] = [
    { name: "Dashboard", href: getDashboardRoute(role), icon: Home, show: true },
  ];

  if (!role) {
    return baseItems;
  }

  if (role === "SUPERADMIN") {
    console.log("SUPERADMIN MENU");
  return [
    ...baseItems,
    { name: "Users", href: "/users", icon: Users, show: true },
    { name: "Roles", href: "/roles", icon: Shield, show: true },
    { name: "Tenants", href: "/tenants", icon: Building, show: true },
    { name: "Resellers", href: "/reseller", icon: Users, show: true },
  ];
}

  if (role === "RESELLER") {
    return [
      ...baseItems,
      { name: "Tenants", href: "/tenants", icon: Building, show: true },
    ];
  }

  const canManageUsers =
    role === "SUPERADMIN" || role === "ADMIN" ||
    permissions?.has("admin:user_manage") ||
    permissions?.has("user:read");
    
    

  const canManageRoles = permissions?.has("admin:role_manage") ||
    role === "SUPERADMIN" ||
    role === "ADMIN";

  const canManageTenants = role === "SUPERADMIN" || role === "RESELLER";

  const canViewLeads = permissions?.has("lead:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewAccounts = permissions?.has("account:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewContacts = permissions?.has("contact:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewDeals = permissions?.has("deal:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewTasks = permissions?.has("task:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewCalls = permissions?.has("call:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";

  const canViewMeetings = permissions?.has("meeting:read") || role === "ADMIN" || role === "MANAGER" || role === "EMPLOYEE";


  return [
    ...baseItems,
    ...(canViewDeals? [{ name: "Deals", href: "/deals", icon: Target, show: true }] : []),
    ...(canViewLeads ? [{ name: "Leads", href: "/leads", icon: Target, show: true }] : []),
    ...(canViewAccounts ? [{ name: "Accounts", href: "/accounts", icon: Building, show: true }] : []),
    ...(canViewContacts ? [{ name: "Contacts", href: "/contacts", icon: Users, show: true }] : []),
    ...(canViewTasks ? [{ name: "Tasks", href: "/tasks", icon: CheckSquare, show: true }] : []),
    ...(canViewCalls ? [{ name: "Calls", href: "/calls", icon: Phone, show: true }] : []),
    ...(canViewMeetings ? [{ name: "Meetings", href: "/meetings", icon: Calendar, show: true }] : []),
    ...(canManageUsers ? [{ name: "Users", href: "/users", icon: Users, show: true }] : []),
    ...(canManageRoles ? [{ name: "Roles", href: "/roles", icon: Shield, show: true }] : []),
    ...(canManageTenants ? [{ name: "Tenants", href: "/tenants", icon: Building, show: true }] : []),
  ];
}
