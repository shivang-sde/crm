import { useAuthStore } from "@/lib/store/authStore";

export type AccessScope = "ALL" | "TEAM" | "OWN" | "NONE";

/**
 * Canonical frontend authorization API.
 *
 * Single source of truth: the auth store permission map
 * (`module:action` -> accessScope) loaded from the user's assigned role.
 *
 * Semantics mirror the backend (RBAC-1):
 *   - missing permission          => false / "NONE"
 *   - scope NONE                  => false / "NONE"
 *   - ALL / TEAM / OWN            => true for capability visibility;
 *                                    the scope value describes data reach
 *                                    (record filtering is RBAC-7 scope).
 *   - unknown role names          => irrelevant; tenant-created roles work
 *                                    purely through the permission map.
 *
 * This hook never consults role names and never defaults to allow.
 */
export const usePermissions = () => {
  const authHasPermission = useAuthStore((state) => state.hasPermission);
  const authGetAccessScope = useAuthStore((state) => state.getAccessScope);
  const permissions = useAuthStore((state) => state.permissions);
  const permissionsLoaded = useAuthStore((state) => state.permissionsLoaded);

  /**
   * Capability check. Accepts ("lead", "read") or ("lead:read").
   * Fail-closed: false while permissions are loading, malformed, or absent.
   */
  const hasPermission = (module: string, action?: string): boolean => {
    if (!module) {
      return false;
    }

    if (action) {
      return authHasPermission(module, action);
    }

    const [permissionModule, permissionAction] = module.split(":");
    if (!permissionModule || !permissionAction) {
      return false;
    }

    return authHasPermission(permissionModule, permissionAction);
  };

  /** Alias of hasPermission for call-site readability. */
  const canAccess = hasPermission;

  /**
   * Returns the access scope for a granted permission:
   * "ALL" | "TEAM" | "OWN", or "NONE" when not granted/not loaded.
   * Accepts ("lead", "read") or ("lead:read").
   */
  const getAccessScope = (module: string, action?: string): AccessScope => {
    if (!module) {
      return "NONE";
    }

    if (action) {
      return authGetAccessScope(module, action) as AccessScope;
    }

    const [permissionModule, permissionAction] = module.split(":");
    if (!permissionModule || !permissionAction) {
      return "NONE";
    }

    return authGetAccessScope(permissionModule, permissionAction) as AccessScope;
  };

  /** Alias of getAccessScope. */
  const getScope = getAccessScope;

  // ---- Compatibility flags (all strictly permission-derived; no role names) ----

  // Admin surfaces mirror the backend's dual enforcement paths:
  // tenant context requires admin:user_manage; platform context uses user:*.
  const canViewUsers =
    hasPermission("admin", "user_manage") || hasPermission("user", "read");
  const canManageRoles = hasPermission("admin", "role_manage");
  const canViewTenants = hasPermission("tenant", "read");

  const canViewLeads = hasPermission("lead", "read");
  const canEditLeads = hasPermission("lead", "write");
  const canDeleteLeads = hasPermission("lead", "delete");

  const canViewContacts = hasPermission("contact", "read");
  const canEditContacts = hasPermission("contact", "write");
  const canDeleteContacts = hasPermission("contact", "delete");

  const canViewAccounts = hasPermission("account", "read");
  const canEditAccounts = hasPermission("account", "write");
  const canDeleteAccounts = hasPermission("account", "delete");

  const canViewDeals = hasPermission("deal", "read");
  const canEditDeals = hasPermission("deal", "write");
  const canDeleteDeals = hasPermission("deal", "delete");

  const canViewOfferings = hasPermission("offering", "read");
  const canEditOfferings = hasPermission("offering", "write");
  const canDeleteOfferings = hasPermission("offering", "delete");

  const canViewReports = hasPermission("report", "read");
  const canExportReports = hasPermission("report", "export");

  const canViewTasks = hasPermission("task", "read");
  const canEditTasks = hasPermission("task", "write");
  const canDeleteTasks = hasPermission("task", "delete");

  const canViewCalls = hasPermission("call", "read");
  const canEditCalls = hasPermission("call", "write");
  const canDeleteCalls = hasPermission("call", "delete");

  const canViewMeetings = hasPermission("meeting", "read");
  const canEditMeetings = hasPermission("meeting", "write");
  const canDeleteMeetings = hasPermission("meeting", "delete");

  const canViewActivities = hasPermission("activity", "read");
  const canEditActivities = hasPermission("activity", "write");
  const canDeleteActivities = hasPermission("activity", "delete");

  const canViewEntitlements = hasPermission("entitlement", "read");
  const canEditEntitlements = hasPermission("entitlement", "write");

  const canViewAcquisition = hasPermission("acquisition", "read");
  const canEditAcquisition = hasPermission("acquisition", "write");
  const canDeleteAcquisition = hasPermission("acquisition", "delete");

  const canViewWorkflows = hasPermission("workflow", "read");
  const canEditWorkflows = hasPermission("workflow", "write");
  const canDeleteWorkflows = hasPermission("workflow", "delete");

  return {
    // Canonical API
    hasPermission,
    canAccess,
    getAccessScope,
    getScope,
    permissions,
    permissionsLoaded,
    permissionsLoading: !permissionsLoaded,

    // Compatibility flags
    canViewUsers,
    canManageRoles,
    canViewTenants,
    canViewLeads,
    canEditLeads,
    canDeleteLeads,
    canViewContacts,
    canEditContacts,
    canDeleteContacts,
    canViewAccounts,
    canEditAccounts,
    canDeleteAccounts,
    canViewDeals,
    canEditDeals,
    canDeleteDeals,
    canViewOfferings,
    canEditOfferings,
    canDeleteOfferings,
    canViewReports,
    canExportReports,
    canViewTasks,
    canEditTasks,
    canDeleteTasks,
    canViewCalls,
    canEditCalls,
    canDeleteCalls,
    canViewMeetings,
    canEditMeetings,
    canDeleteMeetings,
    canViewActivities,
    canEditActivities,
    canDeleteActivities,
    canViewEntitlements,
    canEditEntitlements,
    canViewAcquisition,
    canEditAcquisition,
    canDeleteAcquisition,
    canViewWorkflows,
    canEditWorkflows,
    canDeleteWorkflows,

    // Scope readers (data-reach hints; record filtering is RBAC-7)
    getLeadScope: () => getAccessScope("lead", "read"),
    getDealScope: () => getAccessScope("deal", "read"),
    getTaskScope: () => getAccessScope("task", "read"),
    getCallScope: () => getAccessScope("call", "read"),
    getMeetingScope: () => getAccessScope("meeting", "read"),
  };
};
