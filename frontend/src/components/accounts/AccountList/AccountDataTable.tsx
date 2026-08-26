"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
  ColumnDef,
  SortingState,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Building2,
  ChevronLeft,
  ChevronRight,
  Eye,
  Mail,
  MoreHorizontal,
  Pencil,
  Phone,
  Search,
  Trash2,
  UserRound,
} from "lucide-react";

import type { AccountResponse } from "@/types/accounts";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Spinner } from "@/components/ui/spinner";
import { useUserLookup } from "@/lib/hooks/useUserLookup";

export interface AccountFilters {
  page: number;
  size: number;
  sort: string;
  search: string;
}

interface AccountDataTableProps {
  data: AccountResponse[];
  isLoading: boolean;

  filters: AccountFilters;
  onFiltersChange: (filters: AccountFilters) => void;

  totalElements: number;
  totalPages: number;

  canEdit?: boolean;
  canDelete?: boolean;

  onView?: (account: AccountResponse) => void;
  onEdit?: (account: AccountResponse) => void;
  onDelete?: (account: AccountResponse) => void;
}

function formatDate(value?: string | null): string {
  if (!value) {
    return "Not available";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Invalid date";
  }

  return date.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function formatEnumLabel(value?: string | null): string {
  if (!value) {
    return "Not set";
  }

  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function SortableHeader({
  label,
  columnId,
  filters,
  onFiltersChange,
}: {
  label: string;
  columnId: string;
  filters: AccountFilters;
  onFiltersChange: (filters: AccountFilters) => void;
}) {
  const [currentColumn, currentDirection] = filters.sort.split(",");

  const isActive = currentColumn === columnId;
  const direction = isActive ? currentDirection : undefined;

  const handleSort = () => {
    const nextDirection =
      isActive && currentDirection === "asc" ? "desc" : "asc";

    onFiltersChange({
      ...filters,
      sort: `${columnId},${nextDirection}`,
      page: 0,
    });
  };

  return (
    <Button
      type="button"
      variant="ghost"
      className="-ml-3 h-8 px-3"
      onClick={handleSort}
    >
      {label}

      {!isActive && <ArrowUpDown className="ml-2 h-4 w-4" />}

      {direction === "asc" && (
        <ArrowUp className="ml-2 h-4 w-4" />
      )}

      {direction === "desc" && (
        <ArrowDown className="ml-2 h-4 w-4" />
      )}
    </Button>
  );
}

export function AccountDataTable({
  data,
  isLoading,
  filters,
  onFiltersChange,
  totalElements,
  totalPages,
  canEdit = true,
  canDelete = false,
  onView,
  onEdit,
  onDelete,
}: AccountDataTableProps) {
  const { resolveUserName } = useUserLookup();

  const columns = useMemo<ColumnDef<AccountResponse>[]>(
    () => [
      {
        accessorKey: "name",
        header: () => (
          <SortableHeader
            label="Account"
            columnId="name"
            filters={filters}
            onFiltersChange={onFiltersChange}
          />
        ),
        cell: ({ row }) => {
          const account = row.original;

          return (
            <div className="flex min-w-[220px] items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted">
                <Building2 className="h-4 w-4 text-muted-foreground" />
              </div>

              <div className="min-w-0">
                <Link
                  href={`/accounts/${account.id}`}
                  className="block truncate font-medium text-foreground hover:text-primary hover:underline"
                  onClick={(event) => event.stopPropagation()}
                >
                  {account.name}
                </Link>

                {account.email && (
                  <p className="mt-0.5 max-w-[230px] truncate text-xs text-muted-foreground">
                    {account.email}
                  </p>
                )}
              </div>
            </div>
          );
        },
      },
      {
        accessorKey: "email",
        header: "Email",
        cell: ({ row }) => {
          const email = row.original.email;

          if (!email) {
            return (
              <span className="text-sm text-muted-foreground">
                Not available
              </span>
            );
          }

          return (
            <a
              href={`mailto:${email}`}
              className="flex max-w-[240px] items-center gap-2 text-sm hover:text-primary hover:underline"
              onClick={(event) => event.stopPropagation()}
            >
              <Mail className="h-4 w-4 shrink-0 text-muted-foreground" />

              <span className="truncate">{email}</span>
            </a>
          );
        },
      },
      {
        accessorKey: "phone",
        header: "Phone",
        cell: ({ row }) => {
          const phoneNumber = row.original.phone;

          if (!phoneNumber) {
            return (
              <span className="text-sm text-muted-foreground">
                Not available
              </span>
            );
          }

          return (
            <a
              href={`tel:${phoneNumber}`}
              className="flex min-w-[150px] items-center gap-2 text-sm hover:text-primary hover:underline"
              onClick={(event) => event.stopPropagation()}
            >
              <Phone className="h-4 w-4 shrink-0 text-muted-foreground" />
              {phoneNumber}
            </a>
          );
        },
      },
      {
        accessorKey: "industry",
        header: () => (
          <SortableHeader
            label="Industry"
            columnId="industry"
            filters={filters}
            onFiltersChange={onFiltersChange}
          />
        ),
        cell: ({ row }) => {
          const industry = row.original.industry;

          return industry ? (
            <span className="text-sm">
              {formatEnumLabel(industry)}
            </span>
          ) : (
            <span className="text-sm text-muted-foreground">
              Not set
            </span>
          );
        },
      },
      {
        accessorKey: "ownerUserId",
        header: "Owner",
        cell: ({ row }) => {
          const account = row.original;

          /*
           * Prefer an ownerName/assigneeName property if your backend
           * response exposes it. For now, this falls back to ownerUserId.
           */
          const ownerName = resolveUserName(account.ownerUserId);

          if (!account.ownerUserId) {
            return (
              <span className="text-sm text-muted-foreground">
                Not assigned
              </span>
            );
          }

          return (
            <div className="flex max-w-[190px] items-center gap-2">
              <UserRound className="h-4 w-4 shrink-0 text-muted-foreground" />

              <span
                className="truncate text-sm"
                title={String(ownerName)}
              >
                {String(ownerName)}
              </span>
            </div>
          );
        },
      },
      {
        accessorKey: "createdAt",
        header: () => (
          <SortableHeader
            label="Created"
            columnId="createdAt"
            filters={filters}
            onFiltersChange={onFiltersChange}
          />
        ),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-sm text-muted-foreground">
            {formatDate(row.original.createdAt)}
          </span>
        ),
      },
      {
        id: "actions",
        enableSorting: false,
        header: () => (
          <div className="text-right">Actions</div>
        ),
        cell: ({ row }) => {
          const account = row.original;

          return (
            <div className="flex justify-end">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Actions for ${account.name}`}
                    onClick={(event) => event.stopPropagation()}
                  >
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>

                <DropdownMenuContent
                  align="end"
                  className="w-40"
                  onClick={(event) => event.stopPropagation()}
                >
                  <DropdownMenuItem asChild>
                    <Link href={`/accounts/${account.id}`}>
                      <Eye className="mr-2 h-4 w-4" />
                      View
                    </Link>
                  </DropdownMenuItem>

                  {canEdit && (
                    <DropdownMenuItem asChild>
                      <Link href={`/accounts/${account.id}/edit`}>
                        <Pencil className="mr-2 h-4 w-4" />
                        Edit
                      </Link>
                    </DropdownMenuItem>
                  )}

                  {canDelete && onDelete && (
                    <>
                      <DropdownMenuSeparator />

                      <DropdownMenuItem
                        className="text-destructive focus:text-destructive"
                        onClick={() => onDelete(account)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete
                      </DropdownMenuItem>
                    </>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          );
        },
      },
    ],
    [
      canDelete,
      canEdit,
      filters,
      onDelete,
      onFiltersChange,
    ],
  );

  const sorting: SortingState = useMemo(() => {
    const [id, direction] = filters.sort.split(",");

    if (!id) {
      return [];
    }

    return [
      {
        id,
        desc: direction === "desc",
      },
    ];
  }, [filters.sort]);

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
    },
    manualPagination: true,
    manualSorting: true,
    pageCount: totalPages,
    getCoreRowModel: getCoreRowModel(),
  });

  const startItem =
    totalElements === 0
      ? 0
      : filters.page * filters.size + 1;

  const endItem = Math.min(
    (filters.page + 1) * filters.size,
    totalElements,
  );

  const handleRowOpen = (account: AccountResponse) => {
    if (onView) {
      onView(account);
      return;
    }

    window.location.href = `/accounts/${account.id}`;
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />

          <Input
            value={filters.search}
            placeholder="Search accounts by name, email or phone..."
            className="pl-9"
            onChange={(event) =>
              onFiltersChange({
                ...filters,
                search: event.target.value,
                page: 0,
              })
            }
          />
        </div>

        <Select
          value={String(filters.size)}
          onValueChange={(value) =>
            onFiltersChange({
              ...filters,
              size: Number(value),
              page: 0,
            })
          }
        >
          <SelectTrigger className="w-full sm:w-[140px]">
            <SelectValue />
          </SelectTrigger>

          <SelectContent>
            <SelectItem value="10">10 per page</SelectItem>
            <SelectItem value="20">20 per page</SelectItem>
            <SelectItem value="50">50 per page</SelectItem>
            <SelectItem value="100">100 per page</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="overflow-hidden rounded-md border bg-background">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="whitespace-nowrap"
                    >
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>

            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell
                    colSpan={columns.length}
                    className="h-64 text-center"
                  >
                    <div className="flex items-center justify-center">
                      <Spinner />
                    </div>
                  </TableCell>
                </TableRow>
              ) : table.getRowModel().rows.length === 0 ? (
                <TableRow>
                  <TableCell
                    colSpan={columns.length}
                    className="h-48 text-center"
                  >
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Building2 className="h-9 w-9 text-muted-foreground" />

                      <p className="font-medium">
                        No accounts found
                      </p>

                      <p className="max-w-sm text-sm text-muted-foreground">
                        Try changing your search or create a new
                        account.
                      </p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    onDoubleClick={() =>
                      handleRowOpen(row.original)
                    }
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell
                        key={cell.id}
                        className="align-middle"
                      >
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground">
          Showing {startItem}â€“{endItem} of {totalElements} accounts
        </p>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={isLoading || filters.page === 0}
            onClick={() =>
              onFiltersChange({
                ...filters,
                page: filters.page - 1,
              })
            }
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            Previous
          </Button>

          <span className="min-w-24 text-center text-sm">
            Page {totalPages === 0 ? 0 : filters.page + 1} of{" "}
            {totalPages}
          </span>

          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={
              isLoading ||
              totalPages === 0 ||
              filters.page >= totalPages - 1
            }
            onClick={() =>
              onFiltersChange({
                ...filters,
                page: filters.page + 1,
              })
            }
          >
            Next
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}