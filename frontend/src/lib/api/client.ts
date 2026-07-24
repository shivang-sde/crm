import axios, { AxiosHeaders } from "axios";
import { useAuthStore } from "../store/authStore";
import { ApiResponse, AuthResponse } from "@/types/auth";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://103.117.50.251:8091/api/v1";

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
    const originalRequest = error.config;

    if (
      [401, 403].includes(error.response?.status)  &&
      !originalRequest._retry &&
      !originalRequest.url?.includes("/auth/refresh")
    ) {
      originalRequest._retry = true;

      try {
        const refreshResponse = await axios.post<ApiResponse<AuthResponse>>(
          `${BASE_URL}/auth/refresh`,
          {},
          { withCredentials: true }
        );

        const authPayload = refreshResponse.data.data;

        if (authPayload?.accessToken) {
          useAuthStore
            .getState()
            .setAuth(authPayload.user, authPayload.accessToken, authPayload.user.role || 'EMPLOYEE', authPayload.tenant);

          const headers = new AxiosHeaders(originalRequest.headers);
          headers.set("Authorization", `Bearer ${authPayload.accessToken}`);
          originalRequest.headers = headers;

          return api(originalRequest);
        }
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