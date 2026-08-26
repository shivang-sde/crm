import { ApiResponse } from "@/types/auth";
import type { AxiosError } from "axios";

export const unwrapResponse = <T>(response: { data: ApiResponse<T> }): T => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected API response format");
  }
  return response.data.data;
};

/** Extracts the backend's `{ error: { message } }` body from an axios failure. */
export const apiErrorMessage = (error: unknown, fallback: string): string => {
  const data = (error as AxiosError<{ error?: { message?: string } }>)?.response?.data;
  return data?.error?.message || fallback;
};

export const unwrapListResponse = <T, M = any>(response: { data: ApiResponse<T[]> }): { data: T[]; meta: M } => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected API response format");
  }
  const meta = (response.data.meta || {}) as M;
  return { data: response.data.data, meta };
};
