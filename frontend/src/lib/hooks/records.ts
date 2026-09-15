"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { recordApi, recordDisplayApi, recordFieldApi, recordMappingApi, recordTypeApi, recordWebhookApi, recordWebhookDeliveryApi } from "@/lib/api/records";
import { apiErrorMessage } from "@/lib/api/api-utils";
import {
  RecordFieldCreateRequest,
  RecordFieldUpdateRequest,
  RecordTypeCreateRequest,
  RecordTypeUpdateRequest,
  DisplayConfigRequest,
  CrmRecordCreateRequest,
  CrmRecordUpdateRequest,
  RecordMappingProfileCreateRequest,
  RecordMappingProfileUpdateRequest,
  RecordWebhookCreateRequest,
  RecordWebhookUpdateRequest,
} from "@/types/records";

export function useRecordTypes(page = 0, size = 20) {
  return useQuery({
    queryKey: ["record-types", page, size],
    queryFn: () => recordTypeApi.list(page, size),
  });
}

export function useRecordType(id: string | undefined) {
  return useQuery({
    queryKey: ["record-types", id],
    queryFn: () => recordTypeApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateRecordType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: RecordTypeCreateRequest) => recordTypeApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-types"] });
      toast.success("Record type created");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to create record type")),
  });
}

export function useUpdateRecordType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: RecordTypeUpdateRequest }) => recordTypeApi.update(id, data),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ["record-types"] });
      qc.invalidateQueries({ queryKey: ["record-types", id] });
      toast.success("Record type updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update record type")),
  });
}

export function useDeleteRecordType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recordTypeApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-types"] });
      toast.success("Record type archived");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to delete record type")),
  });
}

export function useRecordFields(recordTypeId: string | undefined) {
  return useQuery({
    queryKey: ["record-fields", recordTypeId],
    queryFn: () => recordFieldApi.list(recordTypeId!),
    enabled: !!recordTypeId,
  });
}

export function useCreateRecordField() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ recordTypeId, data }: { recordTypeId: string; data: RecordFieldCreateRequest }) =>
      recordFieldApi.create(recordTypeId, data),
    onSuccess: (_, { recordTypeId }) => {
      qc.invalidateQueries({ queryKey: ["record-fields", recordTypeId] });
      qc.invalidateQueries({ queryKey: ["record-types"] });
      toast.success("Field created");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to create field")),
  });
}

export function useUpdateRecordField() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      recordTypeId,
      fieldId,
      data,
    }: {
      recordTypeId: string;
      fieldId: string;
      data: RecordFieldUpdateRequest;
    }) => recordFieldApi.update(recordTypeId, fieldId, data),
    onSuccess: (_, { recordTypeId }) => {
      qc.invalidateQueries({ queryKey: ["record-fields", recordTypeId] });
      toast.success("Field updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update field")),
  });
}

export function useDeleteRecordField() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ recordTypeId, fieldId }: { recordTypeId: string; fieldId: string }) =>
      recordFieldApi.delete(recordTypeId, fieldId),
    onSuccess: (_, { recordTypeId }) => {
      qc.invalidateQueries({ queryKey: ["record-fields", recordTypeId] });
      qc.invalidateQueries({ queryKey: ["record-display-config", recordTypeId] });
      toast.success("Field removed");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to delete field")),
  });
}

export function useDisplayConfig(recordTypeId: string | undefined) {
  return useQuery({
    queryKey: ["record-display-config", recordTypeId],
    queryFn: () => recordDisplayApi.get(recordTypeId!),
    enabled: !!recordTypeId,
  });
}

export function usePutDisplayConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ recordTypeId, data }: { recordTypeId: string; data: DisplayConfigRequest }) =>
      recordDisplayApi.put(recordTypeId, data),
    onSuccess: (_, { recordTypeId }) => {
      qc.invalidateQueries({ queryKey: ["record-display-config", recordTypeId] });
      toast.success("Display configuration saved");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to save display config")),
  });
}

export function useResetDisplayConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (recordTypeId: string) => recordDisplayApi.reset(recordTypeId),
    onSuccess: (_, recordTypeId) => {
      qc.invalidateQueries({ queryKey: ["record-display-config", recordTypeId] });
      toast.success("Display reset to default");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to reset display")),
  });
}

export function useRecords(params: { recordTypeId?: string; page?: number; size?: number; search?: string; sort?: string; direction?: string }) {
  const { recordTypeId, page = 0, size = 20, search, sort, direction } = params;
  return useQuery({
    queryKey: ["records", recordTypeId, page, size, search, sort, direction],
    queryFn: () => recordApi.list({ recordTypeId, page, size, search, sort, direction }),
    enabled: !!recordTypeId,
  });
}

export function useRecord(id: string | undefined) {
  return useQuery({
    queryKey: ["record", id],
    queryFn: () => recordApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateRecord() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CrmRecordCreateRequest) => recordApi.create(data),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ["records", vars.recordTypeId] });
      qc.invalidateQueries({ queryKey: ["records"] });
      toast.success("Record created");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to create record")),
  });
}

export function useUpdateRecord() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CrmRecordUpdateRequest }) => recordApi.update(id, data),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["record", res.id] });
      qc.invalidateQueries({ queryKey: ["records", res.recordTypeId] });
      qc.invalidateQueries({ queryKey: ["records"] });
      toast.success("Record updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update record")),
  });
}

export function usePatchRecord() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Record<string, unknown> }) => recordApi.patch(id, data),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["record", res.id] });
      qc.invalidateQueries({ queryKey: ["records", res.recordTypeId] });
      qc.invalidateQueries({ queryKey: ["records"] });
      toast.success("Record updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update record")),
  });
}

export function useDeleteRecord() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recordApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["records"] });
      toast.success("Record archived");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to delete record")),
  });
}

export function useMappingProfiles(params: { recordTypeId?: string; page?: number; size?: number } = {}) {
  const { recordTypeId, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["record-mapping-profiles", recordTypeId, page, size],
    queryFn: () => recordMappingApi.list({ recordTypeId, page, size }),
  });
}

export function useMappingProfile(id: string | undefined) {
  return useQuery({
    queryKey: ["record-mapping-profiles", id],
    queryFn: () => recordMappingApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateMappingProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: RecordMappingProfileCreateRequest) => recordMappingApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-mapping-profiles"] });
      toast.success("Mapping profile created");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to create mapping profile")),
  });
}

export function useUpdateMappingProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: RecordMappingProfileUpdateRequest }) => recordMappingApi.update(id, data),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ["record-mapping-profiles"] });
      qc.invalidateQueries({ queryKey: ["record-mapping-profiles", id] });
      toast.success("Mapping profile updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update mapping profile")),
  });
}

export function useDeleteMappingProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recordMappingApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-mapping-profiles"] });
      toast.success("Mapping profile archived");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to delete mapping profile")),
  });
}

export function useWebhooks(params: { recordTypeId?: string; isActive?: boolean; page?: number; size?: number } = {}) {
  const { recordTypeId, isActive, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["record-webhooks", recordTypeId, isActive, page, size],
    queryFn: () => recordWebhookApi.list({ recordTypeId, isActive, page, size }),
  });
}

export function useWebhook(id: string | undefined) {
  return useQuery({
    queryKey: ["record-webhooks", id],
    queryFn: () => recordWebhookApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: RecordWebhookCreateRequest) => recordWebhookApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-webhooks"] });
      toast.success("Webhook created");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to create webhook")),
  });
}

export function useUpdateWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: RecordWebhookUpdateRequest }) => recordWebhookApi.update(id, data),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ["record-webhooks"] });
      qc.invalidateQueries({ queryKey: ["record-webhooks", id] });
      toast.success("Webhook updated");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to update webhook")),
  });
}

export function useDeleteWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recordWebhookApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["record-webhooks"] });
      toast.success("Webhook archived");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to delete webhook")),
  });
}

export function useRotateWebhookSecret() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => recordWebhookApi.rotateSecret(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ["record-webhooks"] });
      qc.invalidateQueries({ queryKey: ["record-webhooks", id] });
      toast.success("Secret rotated – copy now, it will not be shown again");
    },
    onError: (e: unknown) => toast.error(apiErrorMessage(e, "Failed to rotate secret")),
  });
}

export function useWebhookDeliveries(webhookId: string | undefined, params: { status?: string; page?: number; size?: number } = {}) {
  const { status, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["record-webhook-deliveries", webhookId, status, page, size],
    queryFn: () => recordWebhookDeliveryApi.list(webhookId!, { status, page, size }),
    enabled: !!webhookId,
  });
}

export function useWebhookDelivery(webhookId: string | undefined, deliveryId: string | undefined) {
  return useQuery({
    queryKey: ["record-webhook-delivery", webhookId, deliveryId],
    queryFn: () => recordWebhookDeliveryApi.get(webhookId!, deliveryId!),
    enabled: !!webhookId && !!deliveryId,
  });
}
