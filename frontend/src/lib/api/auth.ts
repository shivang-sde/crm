import axios from "axios";
import { api } from "./client";
import {
  ApiResponse,
  AuthResponse,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  User,
} from "@/types/auth";

const unwrapAuthResponse = <T>(response: { data: ApiResponse<T> }): T => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected auth response format");
  }
  return response.data.data;
};

export const authApi = {
  login: async (data: LoginRequest) => {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/login", data);
    return unwrapAuthResponse(response);
  },

  register: async (data: RegisterRequest) => {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/register", data);
    return unwrapAuthResponse(response);
  },

  refreshAuth: async () => {
    // Use the shared 'api' instance instead of raw 'axios'
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/refresh");
    return unwrapAuthResponse(response);
  },

  logout: async () => {
    await api.post("/auth/logout");
  },

  getCurrentUser: async () => {
    const response = await api.get<ApiResponse<User>>("/auth/me");
    return unwrapAuthResponse(response);
  },

  forgotPassword: async (data: ForgotPasswordRequest) => {
    const response = await api.post<ApiResponse<unknown>>("/auth/forgot-password", data);
    return unwrapAuthResponse(response);
  },

  resetPassword: async (data: ResetPasswordRequest) => {
    const response = await api.post<ApiResponse<unknown>>("/auth/reset-password", data);
    return unwrapAuthResponse(response);
  },
};