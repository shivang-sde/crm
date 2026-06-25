"use client";

import Link from "next/link";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { AccountResponse } from "@/types/accounts";

interface AccountTableProps {
  accounts: AccountResponse[];
}

export function AccountTable({ accounts }: AccountTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Phone</TableHead>
          <TableHead>Industry</TableHead>
          <TableHead>Owner</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {accounts.map((account) => (
          <TableRow key={account.id}>
            <TableCell className="font-medium">
              <Link href={`/accounts/${account.id}`} className="text-primary hover:underline">
                {account.name}
              </Link>
            </TableCell>
            <TableCell>{account.email || "—"}</TableCell>
            <TableCell>{account.phone || "—"}</TableCell>
            <TableCell>{account.industry || "—"}</TableCell>
            <TableCell>{account.ownerUserId || "—"}</TableCell>
            <TableCell className="text-sm text-muted-foreground">
              {new Date(account.createdAt).toLocaleDateString()}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
