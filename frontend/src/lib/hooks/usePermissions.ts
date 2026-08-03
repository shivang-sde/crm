import { useAuthStore } from "@/lib/store/authStore";

export const usePermissions = () => {
  const authHasPermission = useAuthStore((state) => state.hasPermission);
  const authGetAccessScope = useAuthStore((state) => state.getAccessScope);
  const permissions = useAuthStore((state) => state.permissions);

  const hasPermission = (module: string, action?: string) => {
    if (action) {
      return authHasPermission(module, action);
    }

    const [permissionModule, permissionAction] = module.split(':');
    if (!permissionAction) {
      return false;
    }

    return authHasPermission(permissionModule, permissionAction);
  };

  const getAccessScope = (module: string, action?: string) => {
    if (action) {
      return authGetAccessScope(module, action);
    }

    const [permissionModule, permissionAction] = module.split(':');
    if (!permissionAction) {
      return 'NONE';
    }

    return authGetAccessScope(permissionModule, permissionAction);
  };

  const canViewUsers = hasPermission('admin', 'user_manage');
  const canManageRoles = hasPermission('admin', 'role_manage');
  const canViewLeads = hasPermission('lead', 'read');
  const canEditLeads = hasPermission('lead', 'write');
  const canViewContacts = hasPermission('contact', 'read');
  const canEditContacts = hasPermission('contact', 'write');
  const canDeleteContacts = hasPermission('contact', 'delete');
  const canViewAccounts = hasPermission('account', 'read');
  const canEditAccounts = hasPermission('account', 'write');
  const canDeleteAccounts = hasPermission('account', 'delete');
  const canViewDeals = hasPermission('deal', 'read');
  const canEditDeals = hasPermission('deal', 'write');
  const canViewReports = hasPermission('report', 'read');
  const canViewTasks = hasPermission('task', 'read');
  const canEditTasks = hasPermission('task', 'write');
  const canDeleteTasks = hasPermission('task', 'delete');
  const canViewCalls = hasPermission('call', 'read');
  const canEditCalls = hasPermission('call', 'write');
  const canDeleteCalls = hasPermission('call', 'delete');
  const canViewMeetings = hasPermission('meeting', 'read');
  const canEditMeetings = hasPermission('meeting', 'write');
  const canDeleteMeetings = hasPermission('meeting', 'delete');
  const canViewActivities = hasPermission('activity', 'read');
  const canEditActivities = hasPermission('activity', 'write');
  const canDeleteActivities = hasPermission('activity', 'delete');

  return {
    canViewUsers,
    canManageRoles,
    canViewLeads,
    canEditLeads,
    canViewContacts,
    canEditContacts,
    canDeleteContacts,
    canViewAccounts,
    canEditAccounts,
    canDeleteAccounts,
    canViewDeals,
    canEditDeals,
    canViewReports,
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
    permissions,
    getLeadScope: () => getAccessScope('lead', 'read'),
    getDealScope: () => getAccessScope('deal', 'read'),
    getTaskScope: () => getAccessScope('task', 'read'),
    getCallScope: () => getAccessScope('call', 'read'),
    getMeetingScope: () => getAccessScope('meeting', 'read'),
    hasPermission,
    getAccessScope,
  };
};
