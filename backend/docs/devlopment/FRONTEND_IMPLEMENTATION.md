# Lead Management Module - Frontend Implementation Guide

> **Status**: Planning & Architecture  
> **Phase**: 2-3  
> **Stack**: Next.js, TypeScript, TanStack Query, React Hook Form, Tailwind CSS, Shadcn/ui

---

## Overview

This document provides comprehensive guidance for implementing the Lead Management frontend components, pages, and features.

---

## Project Structure

```
frontend/src/
├── app/
│   └── (tenant)/
│       ├── leads/
│       │   ├── page.tsx                 # Lead list view
│       │   ├── kanban/
│       │   │   └── page.tsx             # Kanban board
│       │   ├── [id]/
│       │   │   └── page.tsx             # Lead detail page
│       │   └── new/
│       │       └── page.tsx             # Create lead form
│       └── ...
├── components/
│   └── leads/
│       ├── LeadList/
│       │   ├── LeadList.tsx
│       │   ├── LeadTable.tsx
│       │   ├── LeadFilters.tsx
│       │   ├── LeadSearch.tsx
│       │   └── LeadPagination.tsx
│       ├── LeadKanban/
│       │   ├── LeadKanban.tsx
│       │   ├── LeadColumn.tsx
│       │   ├── LeadCard.tsx
│       │   └── types.ts
│       ├── LeadDetail/
│       │   ├── LeadDetail.tsx
│       │   ├── LeadBasicInfo.tsx
│       │   ├── LeadCustomFields.tsx
│       │   ├── LeadTimeline.tsx
│       │   ├── LeadNotes.tsx
│       │   └── LeadAssignment.tsx
│       ├── LeadForm/
│       │   ├── LeadForm.tsx
│       │   ├── DynamicFieldRenderer.tsx
│       │   ├── DynamicFieldInput.tsx
│       │   └── validation.ts
│       └── shared/
│           ├── StatusBadge.tsx
│           ├── SourceBadge.tsx
│           ├── UserAvatar.tsx
│           └── types.ts
├── hooks/
│   └── leads/
│       ├── useLeads.ts
│       ├── useLead.ts
│       ├── useCreateLead.ts
│       ├── useUpdateLead.ts
│       ├── useDeleteLead.ts
│       ├── useAssignLead.ts
│       ├── useChangeLeadStatus.ts
│       ├── useLeadActivities.ts
│       ├── useLeadNotes.ts
│       ├── useAddLeadNote.ts
│       ├── useDeleteLeadNote.ts
│       ├── useLead CustomFields.ts
│       └── useLeadStatuses.ts
├── lib/
│   └── api/
│       ├── leads.ts                     # API client functions
│       └── types.ts                     # Shared types
├── store/
│   └── leads/
│       ├── leadFilters.ts               # Filter state (Zustand/Atom)
│       └── leadView.ts                  # View mode state (list/kanban)
├── types/
│   └── leads.ts                         # TypeScript types
└── ...
```

---

## Pages Implementation

### 1. Lead List Page (`/leads`)

#### Purpose
Display all leads with searching, filtering, sorting, and pagination.

#### Features
- **Table View**: Paginated table with columns
- **Search**: Real-time search by name, email, phone
- **Filters**: By status, source, owner, converted state
- **Sorting**: By any column
- **Pagination**: Client-side with server-side cursor
- **Actions**: Bulk select, delete, assign

#### Implementation

```typescript
// app/(tenant)/leads/page.tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { LeadList } from '@/components/leads/LeadList/LeadList';
import { LeadFilters } from '@/components/leads/LeadList/LeadFilters';
import { Button } from '@/components/ui/button';
import { Plus } from 'lucide-react';

export default function LeadsPage() {
  const router = useRouter();
  const [filters, setFilters] = useState({
    status: null,
    source: null,
    owner: null,
  });
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);

  return (
    <div className="space-y-6 p-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold">Leads</h1>
        <Button onClick={() => router.push('/leads/new')}>
          <Plus className="mr-2 h-4 w-4" />
          New Lead
        </Button>
      </div>

      <LeadFilters filters={filters} onFiltersChange={setFilters} />

      <LeadList
        filters={filters}
        searchTerm={searchTerm}
        page={page}
        onSearchChange={setSearchTerm}
        onPageChange={setPage}
      />
    </div>
  );
}
```

### 2. Lead Kanban Page (`/leads/kanban`)

#### Purpose
Visual pipeline management with drag-and-drop status changes.

#### Features
- **Kanban Columns**: One per lead status
- **Drag & Drop**: Move cards between columns
- **Quick Actions**: Status change on drop
- **Card Details**: Mini card with key info
- **Filtering**: Filter by owner, source

#### Implementation

```typescript
// app/(tenant)/leads/kanban/page.tsx
'use client';

import { LeadKanban } from '@/components/leads/LeadKanban/LeadKanban';

export default function LeadKanbanPage() {
  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold">Lead Pipeline</h1>
      </div>
      <LeadKanban />
    </div>
  );
}
```

### 3. Lead Detail Page (`/leads/[id]`)

#### Purpose
Display comprehensive lead information with timeline and management options.

#### Sections
1. **Basic Information**: Name, email, phone, company, score
2. **Custom Fields**: Dynamically rendered fields
3. **Status & Assignment**: Change status, assign to user
4. **Timeline**: Activities sorted by date
5. **Notes**: Add, edit, delete notes
6. **Actions**: Convert, archive, delete

#### Implementation

```typescript
// app/(tenant)/leads/[id]/page.tsx
'use client';

import { useLead } from '@/hooks/leads/useLead';
import { LeadDetail } from '@/components/leads/LeadDetail/LeadDetail';
import { Skeleton } from '@/components/ui/skeleton';

export default function LeadDetailPage({ params }: { params: { id: string } }) {
  const { data: lead, isLoading } = useLead(params.id);

  if (isLoading) return <Skeleton className="h-full" />;
  if (!lead) return <div>Lead not found</div>;

  return (
    <div className="p-6">
      <LeadDetail lead={lead} />
    </div>
  );
}
```

### 4. Create Lead Page (`/leads/new`)

#### Purpose
Form for creating new leads with custom fields.

#### Features
- **Standard Fields**: Name, email, phone, company
- **Dynamic Fields**: Based on tenant configuration
- **Source Selection**: Lead source dropdown
- **Owner Assignment**: Select owner
- **Validation**: Real-time with Zod
- **Loading State**: Submit button disabled during submission

#### Implementation

```typescript
// app/(tenant)/leads/new/page.tsx
'use client';

import { LeadForm } from '@/components/leads/LeadForm/LeadForm';
import { useRouter } from 'next/navigation';

export default function NewLeadPage() {
  const router = useRouter();

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">Create New Lead</h1>
      <LeadForm
        onSuccess={(lead) => {
          router.push(`/leads/${lead.id}`);
        }}
      />
    </div>
  );
}
```

---

## Components Implementation

### 1. LeadTable Component

```typescript
// components/leads/LeadList/LeadTable.tsx
'use client';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Checkbox } from '@/components/ui/checkbox';
import { StatusBadge } from '../shared/StatusBadge';
import { SourceBadge } from '../shared/SourceBadge';
import { UserAvatar } from '../shared/UserAvatar';
import { LeadResponse } from '@/types/leads';
import Link from 'next/link';

interface LeadTableProps {
  leads: LeadResponse[];
  selectedIds: string[];
  onSelectionChange: (ids: string[]) => void;
}

export function LeadTable({
  leads,
  selectedIds,
  onSelectionChange,
}: LeadTableProps) {
  const toggleSelect = (id: string) => {
    setSelectedIds(
      selectedIds.includes(id)
        ? selectedIds.filter(sid => sid !== id)
        : [...selectedIds, id]
    );
  };

  const toggleSelectAll = () => {
    onSelectionChange(
      selectedIds.length === leads.length
        ? []
        : leads.map(l => l.id)
    );
  };

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-12">
            <Checkbox
              checked={selectedIds.length === leads.length}
              onChange={toggleSelectAll}
            />
          </TableHead>
          <TableHead>Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Phone</TableHead>
          <TableHead>Company</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Owner</TableHead>
          <TableHead>Score</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {leads.map(lead => (
          <TableRow key={lead.id}>
            <TableCell>
              <Checkbox
                checked={selectedIds.includes(lead.id)}
                onChange={() => toggleSelect(lead.id)}
              />
            </TableCell>
            <TableCell>
              <Link
                href={`/leads/${lead.id}`}
                className="font-medium text-blue-600 hover:underline"
              >
                {lead.firstName} {lead.lastName}
              </Link>
            </TableCell>
            <TableCell>{lead.email}</TableCell>
            <TableCell>{lead.phone}</TableCell>
            <TableCell>{lead.company}</TableCell>
            <TableCell>
              <StatusBadge status={lead.status} />
            </TableCell>
            <TableCell>
              <SourceBadge source={lead.source} />
            </TableCell>
            <TableCell>
              <UserAvatar userId={lead.ownerUserId} />
            </TableCell>
            <TableCell>
              <div className="w-20">
                <div className="flex h-2 w-full overflow-hidden rounded-full bg-gray-200">
                  <div
                    className="bg-blue-600"
                    style={{ width: `${lead.score}%` }}
                  />
                </div>
                <span className="text-xs text-gray-600">{lead.score}/100</span>
              </div>
            </TableCell>
            <TableCell className="text-sm text-gray-600">
              {new Date(lead.createdAt).toLocaleDateString()}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

### 2. DynamicFieldRenderer Component

```typescript
// components/leads/LeadForm/DynamicFieldRenderer.tsx
'use client';

import { useMemo } from 'react';
import { LeadCustomFieldResponse } from '@/types/leads';
import { DynamicFieldInput } from './DynamicFieldInput';

interface DynamicFieldRendererProps {
  fields: LeadCustomFieldResponse[];
  values: Record<string, any>;
  errors: Record<string, string>;
  onChange: (fieldKey: string, value: any) => void;
}

export function DynamicFieldRenderer({
  fields,
  values,
  errors,
  onChange,
}: DynamicFieldRendererProps) {
  const sortedFields = useMemo(
    () =>
      fields
        .filter(f => f.isActive)
        .sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0)),
    [fields]
  );

  return (
    <div className="space-y-4">
      {sortedFields.map(field => (
        <DynamicFieldInput
          key={field.id}
          field={field}
          value={values[field.fieldKey]}
          error={errors[field.fieldKey]}
          onChange={value => onChange(field.fieldKey, value)}
        />
      ))}
    </div>
  );
}
```

### 3. DynamicFieldInput Component

```typescript
// components/leads/LeadForm/DynamicFieldInput.tsx
'use client';

import {
  FormControl,
  FormDescription,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import {
  Input,
  Textarea,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Checkbox,
  DatePicker,
} from '@/components/ui/';
import { LeadCustomFieldResponse } from '@/types/leads';

interface DynamicFieldInputProps {
  field: LeadCustomFieldResponse;
  value: any;
  error?: string;
  onChange: (value: any) => void;
}

export function DynamicFieldInput({
  field,
  value,
  error,
  onChange,
}: DynamicFieldInputProps) {
  const renderField = () => {
    const baseProps = {
      value: value || '',
      onChange: (e: any) => onChange(e.target?.value ?? e),
      disabled: false,
    };

    switch (field.fieldType) {
      case 'TEXT':
        return <Input placeholder={field.fieldLabel} {...baseProps} />;

      case 'TEXTAREA':
        return <Textarea placeholder={field.fieldLabel} {...baseProps} />;

      case 'NUMBER':
        return (
          <Input
            type="number"
            placeholder={field.fieldLabel}
            {...baseProps}
          />
        );

      case 'EMAIL':
        return (
          <Input
            type="email"
            placeholder={field.fieldLabel}
            {...baseProps}
          />
        );

      case 'PHONE':
        return (
          <Input
            type="tel"
            placeholder={field.fieldLabel}
            {...baseProps}
          />
        );

      case 'DATE':
        return (
          <DatePicker
            value={value}
            onChange={onChange}
            placeholder={field.fieldLabel}
          />
        );

      case 'BOOLEAN':
        return (
          <Checkbox
            checked={value || false}
            onChange={e => onChange(e.target.checked)}
          />
        );

      case 'SELECT':
        return (
          <Select value={value} onValueChange={onChange}>
            <SelectTrigger>
              <SelectValue placeholder={field.fieldLabel} />
            </SelectTrigger>
            <SelectContent>
              {field.options?.map(opt => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );

      case 'MULTISELECT':
        return (
          <div className="space-y-2">
            {field.options?.map(opt => (
              <div key={opt.value} className="flex items-center space-x-2">
                <Checkbox
                  id={opt.value}
                  checked={(value || []).includes(opt.value)}
                  onChange={e => {
                    const newValue = e.target.checked
                      ? [...(value || []), opt.value]
                      : (value || []).filter((v: string) => v !== opt.value);
                    onChange(newValue);
                  }}
                />
                <label htmlFor={opt.value} className="cursor-pointer">
                  {opt.label}
                </label>
              </div>
            ))}
          </div>
        );

      case 'URL':
        return (
          <Input
            type="url"
            placeholder={field.fieldLabel}
            {...baseProps}
          />
        );

      default:
        return <Input placeholder={field.fieldLabel} {...baseProps} />;
    }
  };

  return (
    <FormItem>
      <FormLabel>
        {field.fieldLabel}
        {field.isRequired && <span className="text-red-500 ml-1">*</span>}
      </FormLabel>
      <FormControl>{renderField()}</FormControl>
      {error && <FormMessage>{error}</FormMessage>}
    </FormItem>
  );
}
```

### 4. LeadForm Component

```typescript
// components/leads/LeadForm/LeadForm.tsx
'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { leadFormSchema, LeadFormData } from './validation';
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { useCreateLead } from '@/hooks/leads/useCreateLead';
import { useLeadStatuses } from '@/hooks/leads/useLeadStatuses';
import { useLeadSources } from '@/hooks/leads/useLeadSources';
import { useLeadCustomFields } from '@/hooks/leads/useLeadCustomFields';
import { DynamicFieldRenderer } from './DynamicFieldRenderer';
import { LeadResponse } from '@/types/leads';

interface LeadFormProps {
  initialData?: LeadResponse;
  onSuccess?: (lead: LeadResponse) => void;
}

export function LeadForm({ initialData, onSuccess }: LeadFormProps) {
  const { data: statuses } = useLeadStatuses();
  const { data: sources } = useLeadSources();
  const { data: customFields } = useLeadCustomFields();
  const { mutate: createLead, isPending } = useCreateLead();

  const form = useForm<LeadFormData>({
    resolver: zodResolver(leadFormSchema),
    defaultValues: initialData || {},
  });

  const onSubmit = (data: LeadFormData) => {
    createLead(data, {
      onSuccess: onSuccess,
    });
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        {/* Standard Fields */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="firstName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>First Name *</FormLabel>
                <FormControl>
                  <Input placeholder="John" {...field} />
                </FormControl>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="lastName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Last Name</FormLabel>
                <FormControl>
                  <Input placeholder="Doe" {...field} />
                </FormControl>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input type="email" placeholder="john@example.com" {...field} />
                </FormControl>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="phone"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Phone</FormLabel>
                <FormControl>
                  <Input type="tel" placeholder="+91-9876543210" {...field} />
                </FormControl>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="company"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Company</FormLabel>
                <FormControl>
                  <Input placeholder="ABC Corporation" {...field} />
                </FormControl>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="statusId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Status *</FormLabel>
                <Select onValueChange={field.onChange} defaultValue={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select a status" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {statuses?.map(status => (
                      <SelectItem key={status.id} value={status.id}>
                        {status.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormItem>
            )}
          />
        </div>

        {/* Custom Fields */}
        {customFields && customFields.length > 0 && (
          <div className="border-t pt-6">
            <h3 className="font-semibold mb-4">Additional Information</h3>
            <DynamicFieldRenderer
              fields={customFields}
              values={form.watch('customData') || {}}
              errors={{}}
              onChange={(fieldKey, value) => {
                const currentCustomData = form.getValues('customData') || {};
                form.setValue('customData', {
                  ...currentCustomData,
                  [fieldKey]: value,
                });
              }}
            />
          </div>
        )}

        {/* Submit Button */}
        <div className="flex gap-4">
          <Button type="submit" disabled={isPending}>
            {isPending ? 'Creating...' : 'Create Lead'}
          </Button>
          <Button type="button" variant="outline">
            Cancel
          </Button>
        </div>
      </form>
    </Form>
  );
}
```

### 5. LeadKanban Component

```typescript
// components/leads/LeadKanban/LeadKanban.tsx
'use client';

import { useLeads } from '@/hooks/leads/useLeads';
import { useLeadStatuses } from '@/hooks/leads/useLeadStatuses';
import { useState } from 'react';
import { LeadColumn } from './LeadColumn';

export function LeadKanban() {
  const { data: statuses } = useLeadStatuses();
  const [filters, setFilters] = useState({});
  const { data: allLeads } = useLeads(filters, 0, 1000);

  if (!statuses) return <div>Loading...</div>;

  return (
    <div className="overflow-x-auto pb-4">
      <div className="flex gap-4 min-w-full">
        {statuses.map(status => (
          <LeadColumn
            key={status.id}
            status={status}
            leads={allLeads?.filter(l => l.status.id === status.id) || []}
          />
        ))}
      </div>
    </div>
  );
}
```

---

## Hooks Implementation

### 1. useLeads Hook

```typescript
// hooks/leads/useLeads.ts
'use client';

import { useQuery } from '@tanstack/react-query';
import { listLeads } from '@/lib/api/leads';

interface LeadFilters {
  status?: string;
  source?: string;
  owner?: string;
  converted?: boolean;
}

export function useLeads(filters: LeadFilters, page: number, size: number) {
  return useQuery({
    queryKey: ['leads', filters, page, size],
    queryFn: () => listLeads({ ...filters, page, size }),
  });
}
```

### 2. useCreateLead Hook

```typescript
// hooks/leads/useCreateLead.ts
'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createLead } from '@/lib/api/leads';
import { toast } from '@/components/ui/use-toast';

export function useCreateLead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createLead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leads'] });
      toast({
        title: 'Success',
        description: 'Lead created successfully',
      });
    },
    onError: (error) => {
      toast({
        title: 'Error',
        description: 'Failed to create lead',
        variant: 'destructive',
      });
    },
  });
}
```

---

## API Client Functions

### 1. API Client Wrapper

```typescript
// lib/api/leads.ts
'use client';

import { apiClient } from '@/lib/api/client';
import { LeadResponse, LeadCreateRequest } from '@/types/leads';

export async function listLeads(params: {
  page: number;
  size: number;
  search?: string;
  status?: string;
  source?: string;
  owner?: string;
  converted?: boolean;
}) {
  const { data } = await apiClient.get('/leads', { params });
  return data.data;
}

export async function getLead(id: string) {
  const { data } = await apiClient.get(`/leads/${id}`);
  return data.data;
}

export async function createLead(request: LeadCreateRequest) {
  const { data } = await apiClient.post('/leads', request);
  return data.data;
}

export async function updateLead(id: string, request: Partial<LeadCreateRequest>) {
  const { data } = await apiClient.put(`/leads/${id}`, request);
  return data.data;
}

export async function deleteLead(id: string) {
  await apiClient.delete(`/leads/${id}`);
}

// ... more functions
```

---

## Form Validation

```typescript
// components/leads/LeadForm/validation.ts
import { z } from 'zod';

export const leadFormSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().optional(),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
  phone: z.string().optional(),
  company: z.string().optional(),
  statusId: z.string().uuid('Status is required'),
  sourceId: z.string().uuid().optional(),
  ownerUserId: z.string().uuid().optional(),
  score: z.number().min(0).max(100).optional(),
  customData: z.record(z.any()).optional(),
});

export type LeadFormData = z.infer<typeof leadFormSchema>;
```

---

## TypeScript Types

```typescript
// types/leads.ts
export interface LeadResponse {
  id: string;
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  company?: string;
  status: {
    id: string;
    name: string;
    color: string;
    isClosed: boolean;
  };
  source?: {
    id: string;
    name: string;
  };
  ownerUserId?: string;
  score: number;
  notes?: string;
  isConverted: boolean;
  customData: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface LeadCreateRequest {
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  company?: string;
  statusId: string;
  sourceId?: string;
  ownerUserId?: string;
  score?: number;
  notes?: string;
  customData?: Record<string, any>;
}

export interface LeadCustomFieldResponse {
  id: string;
  fieldKey: string;
  fieldLabel: string;
  fieldType: 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'EMAIL' | 'PHONE' | 'DATE' | 'BOOLEAN' | 'SELECT' | 'MULTISELECT' | 'URL';
  isRequired: boolean;
  isActive: boolean;
  displayOrder: number;
  options?: Array<{ label: string; value: string }>;
}
```

---

## State Management (Zustand)

```typescript
// store/leads/leadFilters.ts
import { create } from 'zustand';

interface LeadFiltersState {
  statusId?: string;
  sourceId?: string;
  ownerId?: string;
  searchTerm: string;
  setStatusId: (id?: string) => void;
  setSourceId: (id?: string) => void;
  setOwnerId: (id?: string) => void;
  setSearchTerm: (term: string) => void;
  reset: () => void;
}

export const useLeadFilters = create<LeadFiltersState>(set => ({
  searchTerm: '',
  setStatusId: statusId => set({ statusId }),
  setSourceId: sourceId => set({ sourceId }),
  setOwnerId: ownerId => set({ ownerId }),
  setSearchTerm: searchTerm => set({ searchTerm }),
  reset: () => set({ statusId: undefined, sourceId: undefined, ownerId: undefined, searchTerm: '' }),
}));
```

---

## Implementation Checklist

### Phase 2: Basic UI & Hooks
- [ ] Create lead list page with table
- [ ] Create lead detail page
- [ ] Create lead form with standard fields
- [ ] Create lead filters component
- [ ] Implement TanStack Query hooks
- [ ] Add API client functions
- [ ] Add Zod validation

### Phase 3: Advanced Features
- [ ] Implement kanban board with drag-and-drop
- [ ] Add custom fields rendering
- [ ] Add timeline/activities display
- [ ] Add notes management
- [ ] Add lead conversion flow
- [ ] Implement bulk operations

### Testing
- [ ] Unit tests for hooks
- [ ] Component tests with React Testing Library
- [ ] E2E tests with Playwright

---

## Key Design Patterns

### 1. DynamicFieldRenderer
- Server sends field definitions
- Frontend renders appropriate input
- No hardcoding of fields

### 2. Hook-Based Data Fetching
- Separate hooks for each query/mutation
- Automatic cache invalidation
- Loading/error states built-in

### 3. Form Management
- React Hook Form for efficiency
- Zod for schema validation
- Custom field rendering support

### 4. State Management
- Zustand for filter state
- React Query for server state
- URL params for persistence

---

## Future Enhancements

- [ ] Lead duplication detection
- [ ] Lead scoring algorithm
- [ ] Bulk import/export
- [ ] Email notifications
- [ ] API webhook integration
- [ ] Advanced analytics
- [ ] Lead nurture workflows

---

This frontend implementation guide ensures:
✅ Dynamic field support without hardcoding
✅ Extensible component architecture  
✅ Server-side state management with TanStack Query  
✅ Type-safe development with TypeScript  
✅ Form validation with Zod  
✅ Responsive UI with Tailwind CSS  
✅ Accessibility with Shadcn components
