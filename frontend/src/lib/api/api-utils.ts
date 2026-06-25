import { ApiResponse } from "@/types/auth";

export const unwrapResponse = <T>(response: { data: ApiResponse<T> }): T => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected API response format");
  }
  return response.data.data;
};

export const unwrapListResponse = <T, M = any>(response: { data: ApiResponse<T[]> }): { data: T[]; meta: M } => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected API response format");
  }
  const meta = (response.data.meta || {}) as M;
  return { data: response.data.data, meta };
};
