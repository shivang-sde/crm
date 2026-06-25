export interface EntityActivityResponse {
  id: string;
  entityType: string;
  entityId: string;
  activityType: string;
  description: string;
  performedBy?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface EntityNoteResponse {
  id: string;
  entityType: string;
  entityId: string;
  note: string;
  createdBy?: string;
  createdAt: string;
  updatedAt?: string;
}
