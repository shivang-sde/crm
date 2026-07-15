import React from 'react';
import { useUnifiedActivities, type UnifiedActivitiesFilters } from '@/hooks/tasks/useUnifiedActivities';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { format } from 'date-fns';
import { Calendar, Phone, Users, CheckCircle, Clock } from 'lucide-react';
import type { ActivityType, UnifiedActivity } from '@/types/activity';

interface UnifiedActivityTimelineProps {
  entityType: string;
  entityId: string;
  limit?: number;
}

export function UnifiedActivityTimeline({ entityType, entityId, limit = 10 }: UnifiedActivityTimelineProps) {
  const filters: UnifiedActivitiesFilters = {
    entityType,
    entityId,
    size: limit,
    sort: 'createdAt,desc',
  };

  const { data, isLoading } = useUnifiedActivities(filters);

  if (isLoading) {
    return <div>Loading activities...</div>;
  }

  const getActivityIcon = (type: ActivityType) => {
    switch (type) {
      case 'TASK':
        return <CheckCircle className="h-5 w-5" />;
      case 'CALL':
        return <Phone className="h-5 w-5" />;
      case 'MEETING':
        return <Users className="h-5 w-5" />;
    }
  };

  const getActivityColor = (type: ActivityType) => {
    switch (type) {
      case 'TASK':
        return 'bg-blue-500';
      case 'CALL':
        return 'bg-green-500';
      case 'MEETING':
        return 'bg-purple-500';
    }
  };

  const getStatusBadge = (activity: UnifiedActivity) => {
    let colorClass = 'bg-gray-500';
    
    if (activity.type === 'TASK') {
      switch (activity.status) {
        case 'COMPLETED':
          colorClass = 'bg-green-500';
          break;
        case 'IN_PROGRESS':
          colorClass = 'bg-blue-500';
          break;
        case 'WAITING':
          colorClass = 'bg-yellow-500';
          break;
        case 'CANCELLED':
          colorClass = 'bg-gray-500';
          break;
      }
    } else {
      switch (activity.status) {
        case 'HELD':
          colorClass = 'bg-green-500';
          break;
        case 'PLANNED':
          colorClass = 'bg-blue-500';
          break;
        case 'NOT_HELD':
        case 'CANCELLED':
          colorClass = 'bg-gray-500';
          break;
      }
    }

    return <Badge className={colorClass}>{activity.status}</Badge>;
  };

  const getDisplayDate = (activity: UnifiedActivity) => {
    if (activity.type === 'TASK') {
      return activity.dueDate;
    }
    return activity.startTime;
  };

  const getDisplayLabel = (activity: UnifiedActivity) => {
    if (activity.type === 'TASK') {
      return 'Due';
    }
    if (activity.type === 'CALL') {
      return 'Call Time';
    }
    return 'Meeting Time';
  };

  if (!data?.content || data.content.length === 0) {
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-center text-muted-foreground">No activities found</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Activity Timeline</h3>
      <div className="relative border-l-2 border-gray-200 ml-4 space-y-6">
        {data.content.map((activity: UnifiedActivity) => (
          <div key={`${activity.type}-${activity.id}`} className="ml-6 relative">
            {/* Timeline dot */}
            <div
              className={`absolute -left-[33px] top-0 h-6 w-6 rounded-full ${getActivityColor(activity.type)} flex items-center justify-center text-white`}
            >
              {getActivityIcon(activity.type)}
            </div>

            <Card>
              <CardContent className="pt-4">
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-medium uppercase text-muted-foreground">
                        {activity.type}
                      </span>
                      {getStatusBadge(activity)}
                      {activity.isOverdue && (
                        <Badge variant="destructive" className="flex items-center gap-1">
                          <Clock className="h-3 w-3" /> Overdue
                        </Badge>
                      )}
                    </div>
                    <h4 className="font-semibold mt-1">{activity.subject}</h4>
                    {activity.description && (
                      <p className="text-sm text-muted-foreground mt-1 line-clamp-2">
                        {activity.description}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex flex-wrap gap-4 text-sm text-muted-foreground mt-3">
                  <div className="flex items-center gap-1">
                    <Calendar className="h-4 w-4" />
                    <span>
                      {getDisplayLabel(activity)}:{' '}
                      {getDisplayDate(activity)
                        ? format(new Date(getDisplayDate(activity)!), 'MMM dd, yyyy')
                        : 'Not set'}
                    </span>
                  </div>

                  {activity.location && (
                    <div className="flex items-center gap-1">
                      <Users className="h-4 w-4" />
                      <span>{activity.location}</span>
                    </div>
                  )}

                  {activity.assignedTo && (
                    <div>
                      Assigned to: {activity.assignedTo.name}
                    </div>
                  )}

                  {activity.callType && (
                    <div>
                      Call Type: {activity.callType}
                    </div>
                  )}

                  {activity.phoneNumber && (
                    <div>
                      Phone: {activity.phoneNumber}
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        ))}
      </div>
    </div>
  );
}
