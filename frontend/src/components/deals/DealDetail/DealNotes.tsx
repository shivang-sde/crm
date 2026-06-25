"use client";

import { useState } from "react";
import { Loader2, Trash2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useDealNotes, useAddDealNote, useDeleteDealNote } from "@/lib/hooks/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";

interface DealNotesProps {
  dealId: string;
}

export function DealNotes({ dealId }: DealNotesProps) {
  const [noteText, setNoteText] = useState("");
  const { canEditDeals } = usePermissions();
  const { data: result, isLoading } = useDealNotes(dealId);
  const addNote = useAddDealNote();
  const deleteNote = useDeleteDealNote();

  const notes = result?.data ?? [];

  function handleAdd() {
    const trimmed = noteText.trim();
    if (!trimmed) return;
    addNote.mutate(
      { dealId, note: trimmed },
      { onSuccess: () => setNoteText("") }
    );
  }

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader>
        <CardTitle className="text-base font-semibold text-foreground">Notes</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditDeals && (
          <div className="space-y-2">
            <Textarea
              placeholder="Add a note..."
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              rows={3}
              className="resize-none"
            />
            <Button
              size="sm"
              onClick={handleAdd}
              disabled={!noteText.trim() || addNote.isPending}
            >
              {addNote.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "Add Note"
              )}
            </Button>
          </div>
        )}

        {isLoading ? (
          <div className="flex justify-center py-4">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : notes.length === 0 ? (
          <p className="text-sm text-muted-foreground">No notes yet.</p>
        ) : (
          <ul className="space-y-3">
            {notes.map((n: any) => (
              <li
                key={n.id}
                className="rounded-md border bg-muted/20 p-3 text-sm space-y-2"
              >
                <p className="whitespace-pre-wrap text-foreground">{n.note}</p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-muted-foreground">
                    {new Date(n.createdAt).toLocaleString()}
                  </span>
                  {canEditDeals && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive hover:bg-destructive/10"
                      onClick={() =>
                        deleteNote.mutate({ dealId, noteId: n.id })
                      }
                      disabled={deleteNote.isPending}
                      title="Delete Note"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
