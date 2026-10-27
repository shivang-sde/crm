'use client';

import { useRouter } from 'next/navigation';
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
import { RecordCombobox } from '@/components/common/RecordCombobox';

import { useCreateTicket } from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { apiErrorMessage } from '@/lib/api/api-utils';

const ticketFormSchema = z.object({
  accountId: z.string().min(1, 'Account is required'),
  subject: z
    .string()
    .trim()
    .min(1, 'Subject is required')
    .max(255, 'Subject must not exceed 255 characters'),
  description: z.string().optional(),
  contactId: z.string().optional(),
});

type TicketFormData = z.infer<typeof ticketFormSchema>;

export default function NewTicketPage() {
  const router = useRouter();
  const { canEditTickets } = usePermissions();
  const createTicket = useCreateTicket();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<TicketFormData>({
    resolver: zodResolver(ticketFormSchema),
    defaultValues: { accountId: '', subject: '', description: '', contactId: '' },
  });

  if (!canEditTickets) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to create tickets.</p>
      </div>
    );
  }

  const onSubmit = async (values: TicketFormData) => {
    try {
      const created = await createTicket.mutateAsync({
        accountId: values.accountId,
        subject: values.subject,
        description: values.description?.trim() || undefined,
        contactId: values.contactId || undefined,
      });
      toast.success('Ticket created');
      router.push(`/tickets/${created.id}`);
    } catch (error) {
      // Backend relationship rules are authoritative: the contact must belong to the
      // selected account, which the form cannot verify without duplicating that logic.
      toast.error(apiErrorMessage(error, 'Failed to create ticket'));
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">New Ticket</h1>
        <p className="text-muted-foreground">
          SLA deadlines are applied automatically when the ticket is created.
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="max-w-2xl space-y-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Ticket details</CardTitle>
            <CardDescription>An account is required; a contact is optional.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="accountId">
                Account <span className="text-red-600">*</span>
              </label>
              <RecordCombobox
                entityType="ACCOUNT"
                value={watch('accountId') || undefined}
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
                value={watch('contactId') || undefined}
                onChange={(id) => setValue('contactId', id ?? '')}
              />
              <p className="text-xs text-muted-foreground">
                When supplied, the contact must belong to the selected account.
              </p>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="subject">
                Subject <span className="text-red-600">*</span>
              </label>
              <Input id="subject" placeholder="Short summary" {...register('subject')} />
              {errors.subject && <p className="text-sm text-red-600">{errors.subject.message}</p>}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="description">
                Description
              </label>
              <Textarea rows={6} {...register('description')} />
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" disabled={createTicket.isPending}>
            {createTicket.isPending ? 'Creating…' : 'Create ticket'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => router.push('/tickets')}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
