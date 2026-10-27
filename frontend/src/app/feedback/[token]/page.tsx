"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, Loader2, XCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { publicFeedbackApi } from "@/lib/api/feedback";
import { formatDateTime } from "@/lib/utils";
import {
  FEEDBACK_TYPE_LABELS,
  type PublicFeedbackRequestResponse,
} from "@/types/feedback";

const SCORE_HELP: Record<string, string> = {
  CSAT: "1 = very dissatisfied, 5 = very satisfied",
  NPS: "0 = not at all likely, 10 = extremely likely",
  CES: "1 = very easy, 7 = very difficult",
};

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/20 p-4">
      <Card className="w-full max-w-lg">{children}</Card>
    </div>
  );
}

export default function PublicFeedbackPage() {
  const params = useParams<{ token?: string | string[] }>();
  const rawToken = params?.token;
  const token = typeof rawToken === "string" ? rawToken : rawToken?.[0] ?? "";

  const { data: request, isLoading, error: queryError } =
    useQuery<PublicFeedbackRequestResponse>({
      queryKey: ["public-feedback", token],
      queryFn: () => publicFeedbackApi.get(token),
      enabled: Boolean(token),
      retry: false,
    });

  const [score, setScore] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [submittedAt, setSubmittedAt] = useState<string | null>(null);

  const loadError = !token
    ? "This feedback link is invalid."
    : queryError instanceof Error
      ? queryError.message || "This feedback link is unavailable."
      : queryError
        ? "This feedback link is unavailable."
        : null;

  if (isLoading) {
    return (
      <Shell>
        <CardContent className="pt-6 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading feedback request...
        </CardContent>
      </Shell>
    );
  }

  if (loadError || !request) {
    return (
      <Shell>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-600" />
            Link unavailable
          </CardTitle>
          <CardDescription>
            {loadError ?? "This feedback link could not be found."}
          </CardDescription>
        </CardHeader>
      </Shell>
    );
  }

  if (submitted || request.status === "SUBMITTED") {
    const recordedAt = submittedAt ?? request.submittedAt;
    return (
      <Shell>
        <CardHeader className="text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
            <CheckCircle2 className="h-6 w-6 text-green-600" />
          </div>
          <CardTitle className="mt-4">Thank you</CardTitle>
          <CardDescription>
            Your feedback has been recorded
            {recordedAt ? ` on ${formatDateTime(recordedAt)}` : ""}.
          </CardDescription>
        </CardHeader>
      </Shell>
    );
  }

  if (request.status === "EXPIRED" || request.status === "REVOKED") {
    return (
      <Shell>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <XCircle className="h-5 w-5 text-muted-foreground" />
            Link no longer active
          </CardTitle>
          <CardDescription>
            {request.status === "EXPIRED"
              ? `This feedback link expired on ${formatDateTime(request.expiresAt)}.`
              : "This feedback link has been revoked. Please contact the team if you still want to share feedback."}
          </CardDescription>
        </CardHeader>
      </Shell>
    );
  }

  const scores = Array.from(
    { length: request.maxScore - request.minScore + 1 },
    (_, index) => request.minScore + index
  );

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitError(null);
    if (score == null) {
      setSubmitError("Please choose a rating.");
      return;
    }

    setSubmitting(true);
    try {
      const result = await publicFeedbackApi.submit(token, {
        score,
        comment: comment.trim() || undefined,
      });
      setSubmittedAt(result.submittedAt);
      setSubmitted(true);
    } catch (error) {
      setSubmitError(
        error instanceof Error
          ? error.message
          : "Your feedback could not be submitted. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Shell>
      <CardHeader>
        <CardTitle>How did we do?</CardTitle>
        <CardDescription>
          Rate your recent support experience. This link is single-use and expires on{" "}
          {formatDateTime(request.expiresAt)}.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-5" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label>
              {FEEDBACK_TYPE_LABELS[request.type]} rating
            </Label>
            <p className="text-xs text-muted-foreground">
              {SCORE_HELP[request.type]}
            </p>
            <div
              role="radiogroup"
              aria-label={`${FEEDBACK_TYPE_LABELS[request.type]} rating`}
              className="flex flex-wrap gap-2"
            >
              {scores.map((value) => (
                <label key={value} className="cursor-pointer">
                  <input
                    type="radio"
                    name="score"
                    value={value}
                    checked={score === value}
                    onChange={() => setScore(value)}
                    className="peer sr-only"
                  />
                  <span className="flex h-10 w-10 items-center justify-center rounded-md border text-sm transition-colors hover:bg-muted peer-checked:border-primary peer-checked:bg-primary peer-checked:text-primary-foreground peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50">
                    {value}
                  </span>
                </label>
              ))}
            </div>
          </div>

          <div className="space-y-1">
            <Label htmlFor="comment">Anything else? (optional)</Label>
            <Textarea
              id="comment"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={4000}
              rows={4}
              placeholder="Tell us what went well or what we could improve."
            />
          </div>

          {submitError && (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
              {submitError}
            </div>
          )}

          <Button type="submit" className="w-full" disabled={submitting || score == null}>
            {submitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Submitting...
              </>
            ) : (
              "Submit feedback"
            )}
          </Button>
        </form>
      </CardContent>
    </Shell>
  );
}
