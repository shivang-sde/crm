"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RecordDetail } from "@/components/records/RecordDetail";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordDetailContent() {
  const params = useParams<{ recordId: string }>();
  const recordId = params.recordId;
  return (
    <div className="max-w-4xl mx-auto p-6 space-y-6">
      <Button variant="ghost" size="sm" asChild>
        <Link href="/records">
          <ArrowLeft className="h-4 w-4 mr-1" /> Back to Records
        </Link>
      </Button>
      <RecordDetail recordId={recordId} />
    </div>
  );
}

export default function RecordDetailPage() {
  return (
    <ProtectedRoute>
      <RecordDetailContent />
    </ProtectedRoute>
  );
}
