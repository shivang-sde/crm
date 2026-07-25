import axios, { AxiosHeaders, InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "../store/authStore";
import { ApiResponse, AuthResponse } from "@/types/auth";

const BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "/api/v1";

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;

  if (token) {
    const headers = new AxiosHeaders(config.headers);
    headers.set("Authorization", `Bearer ${token}`);
    config.headers = headers;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest =
      error.config as RetryableRequestConfig | undefined;

    if (!originalRequest) {
      return Promise.reject(error);
    }

    const status = error.response?.status;

    if (
      status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes("/auth/refresh") &&
      !originalRequest.url?.includes("/auth/login")
    ) {
      originalRequest._retry = true;

      try {
        const refreshResponse =
          await axios.post<ApiResponse<AuthResponse>>(
            `${BASE_URL}/auth/refresh`,
            {},
            {
              withCredentials: true,
            }
          );

        const authPayload = refreshResponse.data.data;

        if (!authPayload?.accessToken) {
          throw new Error("Refresh response did not contain an access token");
        }

        useAuthStore.getState().setAuth(
          authPayload.user,
          authPayload.accessToken,
          authPayload.user.role ?? "EMPLOYEE",
          authPayload.tenant
        );

        const headers = new AxiosHeaders(
          originalRequest.headers
        );

        headers.set(
          "Authorization",
          `Bearer ${authPayload.accessToken}`
        );

        originalRequest.headers = headers;

        return api(originalRequest);
      } catch (refreshError) {
        useAuthStore.getState().logout();

        if (typeof window !== "undefined") {
          window.location.replace("/sign-in");
        }

        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);