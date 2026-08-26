"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { userApi } from "@/lib/api/users";

/**
 * Single users request -> Map<userId, displayName>.
 * Use this wherever owner/user names must be rendered; never fetch per row.
 */
export function useUserLookup() {
  const { data: usersResult, isLoading, isError } = useQuery({
    queryKey: ["users", "lookup"],
    queryFn: () => userApi.getUsers({ page: 0 }),
    staleTime: 5 * 60 * 1000,
  });

  const userNameMap = useMemo(() => {
    const map = new Map<string, string>();
    (usersResult?.content ?? []).forEach((user: any) => {
      const name =
        [user.firstName, user.lastName].filter(Boolean).join(" ").trim() ||
        user.displayName ||
        user.email ||
        "Unknown user";
      if (user.id) {
        map.set(user.id, name);
      }
    });
    return map;
  }, [usersResult]);

  const resolveUserName = (userId?: string | null): string =>
    userId ? userNameMap.get(userId) ?? "Unknown User" : "Unassigned";

  return { userNameMap, resolveUserName, users: usersResult?.content ?? [], isLoading };
}
