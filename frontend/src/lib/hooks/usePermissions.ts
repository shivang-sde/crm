import { useAuthStore } from "@/lib/store/authStore";

export const usePermissions = () => {
  const hasPermission = useAuthStore((state) => state.hasPermission);
  const getAccessScope = useAuthStore((state) => state.getAccessScope);
  const permissions = useAuthStore((state) => state.permissions);

  const canViewUsers = hasPermission('admin', 'user_manage');
  const canManageRoles = hasPermission('admin', 'role_manage');
  const canViewLeads = hasPermission('lead', 'read');
  const canEditLeads = hasPermission('lead', 'write');
  const canViewContacts = hasPermission('contact', 'read');
  const canEditContacts = hasPermission('contact', 'write');
  const canViewAccounts = hasPermission('account', 'read');
  const canEditAccounts = hasPermission('account', 'write');
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
    canViewAccounts,
    canEditAccounts,
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
