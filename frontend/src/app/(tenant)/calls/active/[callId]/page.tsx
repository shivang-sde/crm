"use client";

import React from "react";
import { useParams, useRouter } from "next/navigation";
import { useCall } from "@/lib/hooks/calls";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { ArrowLeft, Copy, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import UnknownCallerWorkflow from "@/components/calls/UnknownCallerWorkflow";
import { CallDispositionForm } from "@/components/calls/CallDispositionForm";

export default function ActiveCallPage() {
  const router = useRouter();
  const params = useParams();
  const callId = params?.callId as string | undefined;

  const { data: call, isLoading, refetch } = useCall(callId ?? "");

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner />
      </div>
    );
  }

  if (!call) {
    return (
      <div className="flex flex-col items-center justify-center h-64">
        <p className="text-muted-foreground">Active call not found</p>
        <Button variant="outline" className="mt-4" onClick={() => router.push('/calls')}>Back to calls</Button>
      </div>
    );
  }

  const copy = async (value?: string | null) => {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value);
      toast.success('Copied');
    } catch {
      toast.error('Unable to copy');
    }
  };

  const statusColors: Record<string, string> = {
    PLANNED: 'bg-blue-100 text-blue-800',
    HELD: 'bg-green-100 text-green-800',
    NOT_HELD: 'bg-yellow-100 text-yellow-800',
    CANCELLED: 'bg-red-100 text-red-800',
  };

  const typeColors: Record<string, string> = {
    INCOMING: 'bg-green-100 text-green-800',
    OUTGOING: 'bg-blue-100 text-blue-800',
  };

  const formatDuration = (seconds?: number | null) => {
    if (seconds == null) return '—';
    if (seconds < 60) return `${seconds}s`;
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (m < 60) return `${m}m ${s}s`;
    const h = Math.floor(m / 60);
    const m2 = m % 60;
    return `${h}h ${m2}m ${s}s`;
  };

  const isCompleted = !!call.endTime || ['HELD', 'NOT_HELD', 'CANCELLED'].includes(call.status);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={() => router.push('/calls')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">Active Call</h1>
          <Badge className={typeColors[call.callType]}>{call.callType}</Badge>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" onClick={() => copy(call.phoneNumber)}>
            <Copy className="mr-2 h-4 w-4" />
            Copy number
          </Button>
          {call.externalCallId ? (
            <Button variant="ghost" onClick={() => copy(call.externalCallId)}>
              <ExternalLink className="mr-2 h-4 w-4" />
              Copy external ID
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Call Status</CardTitle>
              <CardDescription>Live details for the active call</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Status</p>
                <Badge className={statusColors[call.status]}>{call.status}</Badge>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Phone</p>
                <p className="text-sm">{call.phoneNumber || 'Unknown'}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Started</p>
                <p className="text-sm">{call.startTime ? new Date(call.startTime).toLocaleString() : '—'}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Duration</p>
                <p className="text-sm">{formatDuration(call.durationSeconds)}</p>
              </div>
              {call.recordingUrl ? (
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Recording</p>
                  <a href={call.recordingUrl} target="_blank" rel="noreferrer" className="text-sm text-primary underline">Listen</a>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Timeline</CardTitle>
              <CardDescription>Call progression events</CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="space-y-3">
                <li>
                  <div className="text-sm text-muted-foreground">Initiated</div>
                  <div className="text-sm">{call.createdAt ? new Date(call.createdAt).toLocaleString() : '—'}</div>
                </li>
                <li>
                  <div className="text-sm text-muted-foreground">Connected</div>
                  <div className="text-sm">{call.startTime ? new Date(call.startTime).toLocaleString() : '—'}</div>
                </li>
                <li>
                  <div className="text-sm text-muted-foreground">Completed</div>
                  <div className="text-sm">{call.endTime ? new Date(call.endTime).toLocaleString() : '—'}</div>
                </li>
              </ul>
            </CardContent>
          </Card>

          {isCompleted && (
            <CallDispositionForm call={call} onSaved={() => refetch()} />
          )}
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Related Entity</CardTitle>
              <CardDescription>Context for the linked CRM record</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {call.entityName ? (
                <div>
                  <p className="text-sm font-medium text-muted-foreground">{call.entityType}</p>
                  <p className="text-sm">{call.entityName}</p>
                  <Button variant="link" onClick={() => {
                    const type = call.entityType?.toLowerCase();
                    const path = type === 'lead' ? 'leads' : type === 'contact' ? 'contacts' : type === 'account' ? 'accounts' : type === 'deal' ? 'deals' : `${type}s`;
                    router.push(`/${path}/${call.entityId}`);
                  }}>Open entity</Button>
                </div>
              ) : (
                <UnknownCallerWorkflow callId={call.id} phone={call.phoneNumber} />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Provider</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm">{call.providerName ?? '—'}</p>
              {call.externalCallId ? <p className="text-sm text-muted-foreground">External ID: {call.externalCallId}</p> : null}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
