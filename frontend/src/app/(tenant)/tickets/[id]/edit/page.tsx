'use client';

import { useRouter, useParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { RecordCombobox } from '@/components/common/RecordCombobox';

import { useTicket, useUpdateTicket } from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { apiErrorMessage } from '@/lib/api/api-utils';

const ticketEditSchema = z.object({
  subject: z
    .string()
    .trim()
    .min(1, 'Subject is required')
    .max(255, 'Subject must not exceed 255 characters'),
  description: z.string().optional(),
  accountId: z.string().min(1, 'Account is required'),
  contactId: z.string().optional(),
});

type TicketEditData = z.infer<typeof ticketEditSchema>;

export default function EditTicketPage() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === 'string' ? rawId : rawId?.[0] ?? '';

  const { canEditTickets } = usePermissions();
  const { data: ticket, isLoading } = useTicket(id);
  const updateTicket = useUpdateTicket();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<TicketEditData>({
    resolver: zodResolver(ticketEditSchema),
    defaultValues: { subject: '', description: '', accountId: '', contactId: '' },
  });

  if (isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (!ticket) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">Ticket not found</p>
      </div>
    );
  }

  if (!canEditTickets) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to edit tickets.</p>
      </div>
    );
  }

  const onSubmit = async (values: TicketEditData) => {
    try {
      // Ownership is absent by design: the backend PATCH cannot change it, and
      // assignment has its own endpoint and permission.
      await updateTicket.mutateAsync({
        id: ticket.id,
        request: {
          subject: values.subject,
          description: values.description?.trim() || undefined,
          accountId: values.accountId,
          contactId: values.contactId || undefined,
        },
      });
      toast.success('Ticket updated');
      router.push(`/tickets/${ticket.id}`);
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Failed to update ticket'));
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Edit Ticket</h1>
        <p className="text-muted-foreground">
          Ownership and status are managed elsewhere: assignment uses its own permission, and
          status uses the ticket status endpoint.
        </p>
      </div>

      <form
        onSubmit={handleSubmit((values) => {
          reset(values);
          return onSubmit(values);
        })}
        className="max-w-2xl space-y-4"
      >
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{ticket.subject}</CardTitle>
            <CardDescription>
              Fields left unchanged keep their current values.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="accountId">
                Account
              </label>
              <RecordCombobox
                entityType="ACCOUNT"
                value={watch('accountId') || ticket.accountId || undefined}
                onChange={(id) => setValue('accountId', id ?? '', { shouldValidate: true })}
              />
              {errors.accountId && (
                <p className="text-sm text-red-600">{errors.accountId.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="contactId">
                Contact
              </label>
              <RecordCombobox
                entityType="CONTACT"
                value={watch('contactId') || ticket.contactId || undefined}
                onChange={(id) => setValue('contactId', id ?? '')}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="subject">
                Subject
              </label>
              <Input
                id="subject"
                defaultValue={ticket.subject}
                {...register('subject')}
              />
              {errors.subject && <p className="text-sm text-red-600">{errors.subject.message}</p>}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="description">
                Description
              </label>
              <Textarea rows={6} defaultValue={ticket.description ?? ''} {...register('description')} />
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" disabled={updateTicket.isPending}>
            {updateTicket.isPending ? 'Saving…' : 'Save changes'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => router.push(`/tickets/${ticket.id}`)}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
