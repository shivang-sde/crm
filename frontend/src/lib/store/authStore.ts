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

      setPermissions: (permissions) => set({ permissions }),

      hasPermission: (module: string, action: string) => {
        const key = `${module}:${action}`;
        return get().permissions.has(key);
      },

      getAccessScope: (module: string, action: string) => {
        const key = `${module}:${action}`;
        return get().permissions.get(key) || 'NONE';
      },

      logout: () =>
        set({
          accessToken: null,
          user: null,
          tenant: null,
          userRole: null,
          isAuthenticated: false,
          permissions: new Map(),
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
      }),

      merge: (persistedState: any, currentState) => ({
        ...currentState,
        ...persistedState,
        permissions: persistedState.permissions ? new Map(persistedState.permissions) : new Map(),
      }),

      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true);
      },
    }
  )
);