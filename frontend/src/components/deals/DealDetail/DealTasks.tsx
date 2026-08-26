"use client";

import { useQuery } from "@tanstack/react-query";
import { Loader2, CheckCircle2, Clock } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { taskApi } from "@/lib/api/tasks";
import { TaskResponse } from "@/types/tasks";

interface DealTasksProps {
  dealId: string;
}

export function DealTasks({ dealId }: DealTasksProps) {
  const { data: result, isLoading } = useQuery({
    queryKey: ["tasks", "DEAL", dealId],
    queryFn: () => taskApi.listTasks({ entityType: "DEAL", entityId: dealId, size: 50 }),
    enabled: !!dealId,
  });

  const tasks = [...(result?.content ?? [])].sort((a, b) => {
    if (!a.dueDate) return 1;
    if (!b.dueDate) return -1;
    return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
  });
  const openTasks = tasks.filter((t) => !t.isClosed);
  const overdueCount = openTasks.filter((t) => t.isOverdue).length;

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-base font-semibold text-foreground">Tasks</CardTitle>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          {overdueCount > 0 && (
            <Badge variant="destructive">{overdueCount} overdue</Badge>
          )}
          <span>{openTasks.length} open</span>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex justify-center py-4">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : tasks.length === 0 ? (
          <p className="text-sm text-muted-foreground">No tasks linked to this deal.</p>
        ) : (
          <ul className="space-y-2">
            {tasks.map((task: TaskResponse) => {
              const isOverdue = task.isOverdue;
              return (
                <li
                  key={task.id}
                  className={`flex items-start justify-between gap-3 rounded-lg border px-3 py-2 ${isOverdue ? "border-destructive/40 bg-destructive/5" : ""}`}
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{task.subject}</p>
                    {task.dueDate && (
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                        {isOverdue ? (
                          <Clock className="h-3 w-3 text-destructive" />
                        ) : (
                          <CheckCircle2 className="h-3 w-3 text-emerald-600" />
                        )}
                        Due {new Date(task.dueDate).toLocaleString()}
                      </p>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    <Badge variant="outline" className="text-[11px]">
                      {task.priority}
                    </Badge>
                    <Badge
                      variant={task.status === "COMPLETED" ? "secondary" : "outline"}
                      className="text-[11px]"
                    >
                      {task.status.replace(/_/g, " ")}
                    </Badge>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
