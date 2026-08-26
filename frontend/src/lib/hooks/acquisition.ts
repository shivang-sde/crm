import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { acquisitionApi } from "@/lib/api/acquisition";
import {
  LeadIngestionConfigCreateRequest,
  LeadIngestionConfigUpdateRequest,
  LeadIngestionEventListParams,
  LeadIngestionFieldMappingRequest,
} from "@/types/acquisition";

export const acquisitionKeys = {
  all: ["acquisition"] as const,
  lists: () => [...acquisitionKeys.all, "list"] as const,
  detail: (id: string) => [...acquisitionKeys.all, "detail", id] as const,
  mappings: (configId: string) =>
    [...acquisitionKeys.all, "mappings", configId] as const,
  sourceFields: (configId: string, eventId: string) =>
    [...acquisitionKeys.all, "sourceFields", configId, eventId] as const,
  targetFields: (configId: string) =>
    [...acquisitionKeys.all, "targetFields", configId] as const,
  events: (configId: string, params?: LeadIngestionEventListParams) =>
    [...acquisitionKeys.all, "events", configId, params ?? {}] as const,
  eventDetail: (configId: string, eventId: string) =>
    [...acquisitionKeys.all, "events", configId, "detail", eventId] as const,
};

export function useAcquisitionConfigs() {
  return useQuery({
    queryKey: acquisitionKeys.lists(),
    queryFn: () => acquisitionApi.listConfigs(),
  });
}

export function useAcquisitionConfig(id?: string) {
  return useQuery({
    queryKey: acquisitionKeys.detail(id ?? ""),
    queryFn: () => acquisitionApi.getConfig(id ?? ""),
    enabled: Boolean(id),
  });
}

export function useCreateAcquisitionConfig() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: LeadIngestionConfigCreateRequest) =>
      acquisitionApi.createConfig(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.lists() });
    },
  });
}

export function useUpdateAcquisitionConfig() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: LeadIngestionConfigUpdateRequest }) =>
      acquisitionApi.updateConfig(id, data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.lists() });
      qc.invalidateQueries({ queryKey: acquisitionKeys.detail(variables.id) });
    },
  });
}

export function useDeleteAcquisitionConfig() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => acquisitionApi.deleteConfig(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.lists() });
    },
  });
}

export function useLeadIngestionMappings(configId?: string) {
  return useQuery({
    queryKey: acquisitionKeys.mappings(configId ?? ""),
    queryFn: () => acquisitionApi.listMappings(configId ?? ""),
    enabled: Boolean(configId),
  });
}

export function useLeadIngestionSourceFields(configId?: string, eventId?: string) {
  return useQuery({
    queryKey: acquisitionKeys.sourceFields(configId ?? "", eventId ?? ""),
    queryFn: () => acquisitionApi.getSourceFields(configId ?? "", eventId ?? ""),
    enabled: Boolean(configId) && Boolean(eventId),
  });
}

export function useLeadIngestionTargetFields(configId?: string) {
  return useQuery({
    queryKey: acquisitionKeys.targetFields(configId ?? ""),
    queryFn: () => acquisitionApi.getTargetFields(configId ?? ""),
    enabled: Boolean(configId),
  });
}

export function useCreateLeadIngestionMapping(configId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: LeadIngestionFieldMappingRequest) =>
      acquisitionApi.createMapping(configId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.mappings(configId) });
    },
  });
}

export function useUpdateLeadIngestionMapping(configId: string, mappingId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: LeadIngestionFieldMappingRequest) =>
      acquisitionApi.updateMapping(configId, mappingId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.mappings(configId) });
      qc.invalidateQueries({
        queryKey: [...acquisitionKeys.mappings(configId), "detail", mappingId],
      });
    },
  });
}

export function useDeleteLeadIngestionMapping(configId: string) {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (mappingId: string) => acquisitionApi.deleteMapping(configId, mappingId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: acquisitionKeys.mappings(configId) });
    },
  });
}

export function useLeadIngestionEvents(configId: string, params: LeadIngestionEventListParams = {}) {
  return useQuery({
    queryKey: acquisitionKeys.events(configId, params),
    queryFn: () => acquisitionApi.listEvents(configId, params),
    enabled: Boolean(configId),
  });
}

export function useLeadIngestionEvent(configId: string, eventId?: string) {
  return useQuery({
    queryKey: acquisitionKeys.eventDetail(configId, eventId ?? ""),
    queryFn: () => acquisitionApi.getEvent(configId, eventId ?? ""),
    enabled: Boolean(configId && eventId),
  });
}