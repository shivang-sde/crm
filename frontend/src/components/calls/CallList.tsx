"use client";

import React from "react";
import { format } from "date-fns";
import {
  Pencil,
  Phone,
  Trash2,
} from "lucide-react";
import { useRouter } from "next/navigation";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { useCalls } from "@/hooks/tasks/useCalls";
import { usePermissions } from "@/hooks/usePermissions";

import type { CallListParams } from "@/lib/api/calls";
import type {
  CallResponse,
  CallStatus,
  CallType,
} from "@/types/calls";

interface CallListProps {
  entityType?: string;
  entityId?: string;
}

const ALL_VALUE = "ALL";

export function CallList({
  entityType,
  entityId,
}: CallListProps) {
  const router = useRouter();
  const { hasPermission } = usePermissions();

  const [params, setParams] =
    React.useState<CallListParams>({
      entityType,
      entityId,
      page: 0,
      size: 10,
      sort: "createdAt,desc",
    });

  const { data, isLoading } = useCalls(params);

  const handleCallTypeChange = (
    value: string
  ) => {
    setParams((prev) => ({
      ...prev,
      page: 0,
      callType:
        value === ALL_VALUE
          ? undefined
          : (value as CallType),
    }));
  };

  const handleStatusChange = (
    value: string
  ) => {
    setParams((prev) => ({
      ...prev,
      page: 0,
      status:
        value === ALL_VALUE
          ? undefined
          : (value as CallStatus),
    }));
  };

  const getStatusColor = (
    status: CallStatus
  ) => {
    switch (status) {
      case "HELD":
        return "bg-green-500";

      case "PLANNED":
        return "bg-blue-500";

      case "NOT_HELD":
        return "bg-yellow-500";

      case "CANCELLED":
        return "bg-gray-500";

      default:
        return "bg-gray-400";
    }
  };

  const getCallTypeColor = (
    type: CallType
  ) => {
    return type === "INCOMING"
      ? "bg-green-600"
      : "bg-blue-600";
  };

  if (isLoading) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">
        Loading calls...
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-col gap-2 sm:flex-row">
          <Input
            placeholder="Search calls..."
            className="w-full sm:w-64"
            value={params.search ?? ""}
            onChange={(event) =>
              setParams((prev) => ({
                ...prev,
                search:
                  event.target.value ||
                  undefined,
                page: 0,
              }))
            }
          />

          <Select
            value={
              params.callType ??
              ALL_VALUE
            }
            onValueChange={
              handleCallTypeChange
            }
          >
            <SelectTrigger className="w-full sm:w-40">
              <SelectValue placeholder="Type" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value={ALL_VALUE}>
                All types
              </SelectItem>

              <SelectItem value="INCOMING">
                Incoming
              </SelectItem>

              <SelectItem value="OUTGOING">
                Outgoing
              </SelectItem>
            </SelectContent>
          </Select>

          <Select
            value={
              params.status ??
              ALL_VALUE
            }
            onValueChange={
              handleStatusChange
            }
          >
            <SelectTrigger className="w-full sm:w-40">
              <SelectValue placeholder="Status" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value={ALL_VALUE}>
                All statuses
              </SelectItem>

              <SelectItem value="PLANNED">
                Planned
              </SelectItem>

              <SelectItem value="HELD">
                Held
              </SelectItem>

              <SelectItem value="NOT_HELD">
                Not Held
              </SelectItem>

              <SelectItem value="CANCELLED">
                Cancelled
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        {hasPermission("call:write") && (
          <Button
            onClick={() =>
              router.push("/calls/new")
            }
          >
            New Call
          </Button>
        )}
      </div>
    </div>
  );
}