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

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

const unwrapAuthResponse = <T>(response: { data: ApiResponse<T> }): T => {
  if (!response.data?.success || response.data.data === undefined) {
    throw new Error("Unexpected auth response format");
  }
  return response.data.data;
};

export const authApi = {
  login: async (data: LoginRequest) => {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/login", data);
    console.log("Login response:", response); // Debug log
    return unwrapAuthResponse(response);
  },

  register: async (data: RegisterRequest) => {
    const response = await api.post<ApiResponse<AuthResponse>>("/auth/register", data);
    console.log("Register response:", response); // Debug log
    return unwrapAuthResponse(response);
  },

  refreshAuth: async () => {
    const response = await axios.post<ApiResponse<AuthResponse>>(
      `${BASE_URL}/auth/refresh`,
      {},
      { withCredentials: true }
    );
    console.log("Refresh auth response:", response); // Debug log

    return unwrapAuthResponse(response);
  },

  logout: async () => {
    await api.post("/auth/logout");
  },

  getCurrentUser: async () => {
    const response = await api.get<ApiResponse<User>>("/auth/me");
    console.log("Get current user response:", response); // Debug log
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
