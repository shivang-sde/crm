"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

import { User, Tenant } from "@/types/auth";

interface AuthState {
  accessToken: string | null;
  user: User | null;
  tenant: Tenant | null;
  userRole: string | null;
  isAuthenticated: boolean;
  hydrated: boolean;
  isLoading: boolean;
  permissions: Map<string, string>;
  permissionsLoaded: boolean;

  setHydrated: (value: boolean) => void;
  setIsLoading: (value: boolean) => void;

  setAuth: (
    user: User,
    accessToken: string,
    role: string,
    tenant?: Tenant
  ) => void;

  setPermissions: (permissions: Map<string, string>) => void;
  hasPermission: (module: string, action: string) => boolean;
  getAccessScope: (module: string, action: string) => string;

  logout: () => void;
}

/**
 * A permission entry grants a capability only when a recognized,
 * non-NONE scope is present. Missing or NONE entries deny access
 * (mirror of backend fail-closed semantics).
 */
const scopeGrantsCapability = (scope: string | undefined): boolean =>
  scope !== undefined && scope !== "" && scope !== "NONE";

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      user: null,
      tenant: null,
      userRole: null,
      isAuthenticated: false,
      hydrated: false,
      isLoading: false,
      permissions: new Map(),
      permissionsLoaded: false,

      setHydrated: (value) =>
        set({
          hydrated: value,
        }),

      setIsLoading: (value) =>
        set({
          isLoading: value,
        }),

      setAuth: (user, accessToken, role, tenant) =>
        set({
          accessToken,
          user,
          userRole: role,
          tenant: tenant || null,
          isAuthenticated: true,
        }),

      setPermissions: (permissions) =>
        set({ permissions, permissionsLoaded: true }),

      hasPermission: (module: string, action: string) => {
        const key = `${module}:${action}`;
        return scopeGrantsCapability(get().permissions.get(key));
      },

      getAccessScope: (module: string, action: string) => {
        const key = `${module}:${action}`;
        return get().permissions.get(key) || "NONE";
      },

      logout: () =>
        set({
          accessToken: null,
          user: null,
          tenant: null,
          userRole: null,
          isAuthenticated: false,
          permissions: new Map(),
          permissionsLoaded: false,
        }),
    }),
    {
      name: "auth-storage",

      storage: createJSONStorage(() => localStorage),

      partialize: (state) => ({
        accessToken: state.accessToken,
        user: state.user,
        tenant: state.tenant,
        userRole: state.userRole,
        isAuthenticated: state.isAuthenticated,
        permissions: Array.from(state.permissions.entries()),
        permissionsLoaded: state.permissionsLoaded,
      }),

      merge: (persistedState: any, currentState) => ({
        ...currentState,
        ...persistedState,
        permissions: persistedState.permissions ? new Map(persistedState.permissions) : new Map(),
        permissionsLoaded: Boolean(persistedState.permissionsLoaded),
      }),

      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true);
      },
    }
  )
);