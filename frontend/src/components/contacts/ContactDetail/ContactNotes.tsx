"use client";

import { useState } from "react";
import { Loader2, Trash2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useAddContactNote, useContactNotes, useDeleteContactNote } from "@/lib/hooks/contacts";

interface ContactNotesProps {
  contactId: string;
}

export function ContactNotes({ contactId }: ContactNotesProps) {
  const [noteText, setNoteText] = useState("");
  const { canEditContacts } = usePermissions();
  const { data: result, isLoading } = useContactNotes(contactId);
  const addNote = useAddContactNote();
  const deleteNote = useDeleteContactNote();

  const notes = result?.data ?? [];

  function handleAdd() {
    const trimmed = noteText.trim();
    if (!trimmed) return;
    addNote.mutate(
      { contactId, note: trimmed },
      { onSuccess: () => setNoteText("") }
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Notes</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditContacts && (
          <div className="space-y-2">
            <Textarea
              placeholder="Add a note..."
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              rows={3}
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
            {notes.map((n) => (
              <li key={n.id} className="rounded-md border bg-muted/20 p-3 text-sm">
                <p className="whitespace-pre-wrap">{n.note}</p>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-muted-foreground">
                    {new Date(n.createdAt).toLocaleString()}
                  </span>
                  {canEditContacts && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive"
                      onClick={() => deleteNote.mutate({ contactId, noteId: n.id })}
                      disabled={deleteNote.isPending}
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
