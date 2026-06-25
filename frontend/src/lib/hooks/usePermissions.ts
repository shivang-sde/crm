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
    permissions,
    getLeadScope: () => getAccessScope('lead', 'read'),
    getDealScope: () => getAccessScope('deal', 'read'),
    hasPermission,
    getAccessScope,
  };
};
