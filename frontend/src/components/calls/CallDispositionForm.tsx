"use client";

import React from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useSaveCallDisposition } from "@/lib/hooks/calls";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { CallDispositionRequest, CallResponse } from "@/types/calls";
import { toast } from "sonner";

interface CallDispositionFormProps {
  call: CallResponse;
  onSaved?: () => void;
}

const dispositionOptions = [
  { value: "CONNECTED", label: "Connected" },
  { value: "NOT_CONNECTED", label: "Not connected" },
  { value: "INTERESTED", label: "Interested" },
  { value: "NOT_INTERESTED", label: "Not interested" },
  { value: "CALLBACK_REQUESTED", label: "Callback requested" },
  { value: "WRONG_NUMBER", label: "Wrong number" },
  { value: "NO_ANSWER", label: "No answer" },
  { value: "BUSY", label: "Busy" },
  { value: "VOICEMAIL", label: "Voicemail" },
  { value: "FOLLOW_UP_REQUIRED", label: "Follow-up required" },
];

export function CallDispositionForm({ call, onSaved }: CallDispositionFormProps) {
  const [disposition, setDisposition] = React.useState(call.disposition ?? "");
  const [notes, setNotes] = React.useState(call.notes ?? "");
  const saveDisposition = useSaveCallDisposition();
  const { canEditCalls } = usePermissions();

  const isCompletedCall = call.status === "HELD" || call.status === "NOT_HELD" || Boolean(call.endTime);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!disposition) {
      toast.error("Select a disposition before saving");
      return;
    }

    const request: CallDispositionRequest = {
      disposition,
      notes: notes.trim() || undefined,
    };

    try {
      await saveDisposition.mutateAsync({ id: call.id, request });
      toast.success("Disposition saved");
      onSaved?.();
    } catch (error) {
      console.error(error);
      toast.error("Failed to save disposition");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Disposition</CardTitle>
        <CardDescription>
          {isCompletedCall
            ? canEditCalls
              ? "Capture the outcome of the call after it has ended."
              : "You do not have permission to save call dispositions."
            : "Disposition will be available after the call ends."}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isCompletedCall ? (
          <form className="space-y-4" onSubmit={canEditCalls ? handleSubmit : (event) => event.preventDefault()}>
            <div className="space-y-2">
              <label className="text-sm font-medium">Disposition</label>
              <Select value={disposition} onValueChange={setDisposition} disabled={!canEditCalls}>
                <SelectTrigger>
                  <SelectValue placeholder="Select a disposition" />
                </SelectTrigger>
                <SelectContent>
                  {dispositionOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Notes</label>
              <Textarea
                rows={4}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Add any notes about the call outcome"
                disabled={!canEditCalls}
              />
            </div>

            {call.disposition ? (
              <div className="rounded border bg-muted/40 p-3 text-sm">
                <p className="font-medium">Last saved disposition</p>
                <p className="text-muted-foreground">{call.disposition}</p>
              </div>
            ) : null}

            <div className="flex justify-end">
              <Button type="submit" disabled={!canEditCalls || saveDisposition.isPending}>
                {saveDisposition.isPending ? "Saving..." : "Save disposition"}
              </Button>
            </div>
          </form>
        ) : (
          <p className="text-sm text-muted-foreground">
            Disposition will be available after the call ends.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

export default CallDispositionForm;
