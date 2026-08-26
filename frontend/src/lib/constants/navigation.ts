import {
  Home,
  Building,
  Shield,
  Users,
  Target,
  Phone,
  Calendar,
  CheckSquare,
  Package,
  Boxes,
  Webhook,
  Workflow
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface NavItem {
  name: string;
  href: string;
  icon: LucideIcon;
  show: boolean;
}

/**
 * ============================================================================
 * ROUTE AUTHORIZATION MODEL (single source of truth)
 * ============================================================================
 * One interpretation of "which permission unlocks which area" is shared by:
 *   - navigation visibility      (getNavigationItems)
 *   - client route guarding      (components/shared/RouteGuard)
 *   - default dashboard choice   (getDefaultRoute)
 *
 * Semantics mirror the backend (RBAC-3 foundation):
 *   missing permission => false, NONE => false, ALL/TEAM/OWN => true.
 * Scope values describe data reach only; record filtering is RBAC-7.
 *
 * Frontend route guarding is UX ONLY. The backend (RbacFilter + method
 * security) remains the authoritative authorization layer.
 * ============================================================================
 */

// Routes reachable without authentication.
export const PUBLIC_ROUTES = [
  "/sign-in",
  "/sign-up",
  "/forgot-password",
  "/reset-password",
];

/**
 * Authenticated-only surfaces with no catalog permission requirement.
 * They are product shells (entry resolver, generic home, no-access notice).
 */
export const AUTH_ONLY_ROUTES = ["/home", "/dashboard", "/no-access"];

/**
 * PLATFORM UX: routes representing platform-level product behavior.
 * Keyed by platform role membership, not by tenant permissions.
 */
export const PLATFORM_ROUTES: { prefix: string; roles: string[] }[] = [
  { prefix: "/superadmin", roles: ["SUPERADMIN"] },
  { prefix: "/reseller", roles: ["RESELLER", "SUPERADMIN"] },
];

/** A grant alternative expressed as [module, action]. */
type PermissionAlternative = [string, string];

/**
 * Permission-gated top-level segments. Matching is EXACT on the first
 * pathname segment ("/leads/123/edit" -> "leads"), so "/lead-settings"
 * can never collide with "/leads".
 */
const ROUTE_PERMISSIONS: { segment: string; anyOf: PermissionAlternative[] }[] = [
  { segment: "leads", anyOf: [["lead", "read"]] },
  { segment: "deals", anyOf: [["deal", "read"]] },
  { segment: "offerings", anyOf: [["offering", "read"]] },
  { segment: "entitlements", anyOf: [["entitlement", "read"]] },
  { segment: "acquisition", anyOf: [["acquisition", "read"]] },
  { segment: "workflows", anyOf: [["workflow", "read"]] },
  { segment: "contacts", anyOf: [["contact", "read"]] },
  { segment: "accounts", anyOf: [["account", "read"]] },
  { segment: "tasks", anyOf: [["task", "read"]] },
  { segment: "calls", anyOf: [["call", "read"]] },
  { segment: "meetings", anyOf: [["meeting", "read"]] },
  { segment: "users", anyOf: [["admin", "user_manage"], ["user", "read"]] },
  { segment: "roles", anyOf: [["admin", "role_manage"]] },
  { segment: "tenants", anyOf: [["tenant", "read"]] },
  { segment: "admin", anyOf: [["admin", "settings"]] },
];

/**
 * Deterministic default-landing priority. Order mirrors the CRM navigation
 * order (CRM modules first), then admin surfaces. Platform roles bypass the
 * chain entirely (see getDefaultRoute).
 */
const DEFAULT_ROUTE_PRIORITY: { anyOf: PermissionAlternative[]; href: string }[] = [
  { anyOf: [["lead", "read"]], href: "/leads" },
  { anyOf: [["deal", "read"]], href: "/deals" },
  { anyOf: [["offering", "read"]], href: "/offerings" },
  { anyOf: [["entitlement", "read"]], href: "/entitlements" },
  { anyOf: [["acquisition", "read"]], href: "/acquisition" },
  { anyOf: [["workflow", "read"]], href: "/workflows" },
  { anyOf: [["contact", "read"]], href: "/contacts" },
  { anyOf: [["account", "read"]], href: "/accounts" },
  { anyOf: [["task", "read"]], href: "/tasks" },
  { anyOf: [["call", "read"]], href: "/calls" },
  { anyOf: [["meeting", "read"]], href: "/meetings" },
  { anyOf: [["admin", "user_manage"], ["user", "read"]], href: "/users" },
  { anyOf: [["admin", "role_manage"]], href: "/roles" },
  { anyOf: [["tenant", "read"]], href: "/tenants" },
  { anyOf: [["admin", "settings"]], href: "/admin/dashboard" },
];

export const NO_ACCESS_ROUTE = "/no-access";

function scopeGrants(
  permissions: Map<string, string> | null | undefined,
  module: string,
  action: string
): boolean {
  if (!permissions) return false;
  const scope = permissions.get(`${module}:${action}`);
  return scope !== undefined && scope !== "" && scope !== "NONE";
}

function grantsAny(
  permissions: Map<string, string> | null | undefined,
  anyOf: PermissionAlternative[]
): boolean {
  return anyOf.some(([module, action]) => scopeGrants(permissions, module, action));
}

function normalizePath(pathname: string): string {
  if (!pathname || pathname === "/") return "/";
  return pathname.length > 1 && pathname.endsWith("/")
    ? pathname.slice(0, -1)
    : pathname;
}

function firstSegment(path: string): string {
  return path.split("/").filter(Boolean)[0] ?? "";
}

function matchesPrefix(path: string, prefix: string): boolean {
  return path === prefix || path.startsWith(`${prefix}/`);
}

export function isPublicRoute(pathname: string): boolean {
  const path = normalizePath(pathname);
  return PUBLIC_ROUTES.some((route) => path === route || path.startsWith(`${route}/`));
}

/**
 * Centralized permission-driven route authorization.
 *
 * - public/platform/auth-only/unknown segments resolve explicitly below.
 * - unknown segments are treated as authenticated product surface and are NOT
 *   permission-gated because no catalog permission exists for them
 *   (fail-closed is preserved where a permission actually exists; backend
 *   remains authoritative for every API).
 */
export function canAccessRoute(
  pathname: string,
  permissions: Map<string, string> | null | undefined,
  role?: string | null
): boolean {
  const path = normalizePath(pathname);

  if (path === "/") return true;

  for (const platformRoute of PLATFORM_ROUTES) {
    if (matchesPrefix(path, platformRoute.prefix)) {
      return !!role && platformRoute.roles.includes(role);
    }
  }

  const segment = firstSegment(path);
  const rule = ROUTE_PERMISSIONS.find((r) => r.segment === segment);
  if (rule) {
    return grantsAny(permissions, rule.anyOf);
  }

  return true;
}

/**
 * Deterministic default landing route derived exclusively from permissions.
 * Falls back to the safe no-access route instead of looping.
 */
export function getDefaultRoute(
  permissions: Map<string, string> | null | undefined,
  role?: string | null
): string {
  // PLATFORM UX: fixed platform landings for platform roles.
  if (role === "SUPERADMIN") return "/superadmin";
  if (role === "RESELLER") return "/reseller";

  for (const entry of DEFAULT_ROUTE_PRIORITY) {
    if (grantsAny(permissions, entry.anyOf)) {
      return entry.href;
    }
  }

  return NO_ACCESS_ROUTE;
}

/**
 * Scope-aware permission lookup for navigation visibility.
 * Mirrors backend semantics: a permission grants a capability only when a
 * recognized non-NONE scope is present. Missing/NONE => no capability.
 * Role names are never consulted for tenant capability decisions.
 */
function hasGrant(
  permissions: Map<string, string> | undefined,
  key: string
): boolean {
  if (!permissions) return false;
  const scope = permissions.get(key);
  return scope !== undefined && scope !== "" && scope !== "NONE";
}

/**
 * Returns navigation items based on the current role.
 * Platform roles (SUPERADMIN, RESELLER) get fixed platform menus.
 * All tenant capabilities are derived exclusively from the permission map,
 * so tenant-created custom roles work without any frontend knowledge of
 * their names.
 */
export function getNavigationItems(role: string | null, permissions?: Map<string, string>): NavItem[] {
  const baseItems: NavItem[] = [
    { name: "Dashboard", href: getDefaultRoute(permissions ?? null, role), icon: Home, show: true },
  ];

  if (!role) {
    return baseItems;
  }

  // PLATFORM UX (not a tenant authorization decision): platform landing menus.
  if (role === "SUPERADMIN") {
    return [
      ...baseItems,
      { name: "Users", href: "/users", icon: Users, show: true },
      { name: "Roles", href: "/roles", icon: Shield, show: true },
      { name: "Tenants", href: "/tenants", icon: Building, show: true },
      { name: "Resellers", href: "/reseller", icon: Users, show: true },
    ];
  }

  // PLATFORM UX (see above).
  if (role === "RESELLER") {
    return [
      ...baseItems,
      { name: "Tenants", href: "/tenants", icon: Building, show: true },
    ];
  }

  const canManageUsers =
    hasGrant(permissions, "admin:user_manage") ||
    hasGrant(permissions, "user:read");

  const canManageRoles = hasGrant(permissions, "admin:role_manage");

  const canManageTenants = hasGrant(permissions, "tenant:read");

  const canViewLeads = hasGrant(permissions, "lead:read");

  const canViewAccounts = hasGrant(permissions, "account:read");

  const canViewContacts = hasGrant(permissions, "contact:read");

  const canViewDeals = hasGrant(permissions, "deal:read");

  const canViewOfferings = hasGrant(permissions, "offering:read");

  const canViewEntitlements = hasGrant(permissions, "entitlement:read");

  const canViewTasks = hasGrant(permissions, "task:read");

  const canViewCalls = hasGrant(permissions, "call:read");

  const canViewMeetings = hasGrant(permissions, "meeting:read");

  const canViewAcquisition = hasGrant(permissions, "acquisition:read");

  const canViewWorkflows = hasGrant(permissions, "workflow:read");

  return [
    ...baseItems,
    ...(canViewLeads ? [{ name: "Leads", href: "/leads", icon: Target, show: true }] : []),
    ...(canViewDeals ? [{ name: "Deals", href: "/deals", icon: Package, show: true }] : []),
    ...(canViewOfferings ? [{ name: "Offerings", href: "/offerings", icon: Boxes, show: true }] : []),
    ...(canViewEntitlements ? [{ name: "Entitlements", href: "/entitlements", icon: Shield, show: true }] : []),
    ...(canViewAcquisition ? [{ name: "Acquisition", href: "/acquisition", icon: Webhook, show: true }] : []),
    ...(canViewWorkflows ? [{ name: "Workflows", href: "/workflows", icon: Workflow, show: true }] : []),
    ...(canViewContacts ? [{ name: "Contacts", href: "/contacts", icon: Users, show: true }] : []),
    ...(canViewAccounts ? [{ name: "Accounts", href: "/accounts", icon: Building, show: true }] : []),
    ...(canViewTasks ? [{ name: "Tasks", href: "/tasks", icon: CheckSquare, show: true }] : []),
    ...(canViewCalls ? [{ name: "Calls", href: "/calls", icon: Phone, show: true }] : []),
    ...(canViewMeetings ? [{ name: "Meetings", href: "/meetings", icon: Calendar, show: true }] : []),
    ...(canManageUsers ? [{ name: "Users", href: "/users", icon: Users, show: true }] : []),
    ...(canManageRoles ? [{ name: "Roles", href: "/roles", icon: Shield, show: true }] : []),
    ...(canManageTenants ? [{ name: "Tenants", href: "/tenants", icon: Building, show: true }] : []),
  ];
}
