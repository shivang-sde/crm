import { api } from "./client";
import { unwrapResponse } from "./api-utils";
import { ApiResponse } from "@/types/auth";

export interface DemoDataStatus {
  templateKey: string;
  templateVersion: number;
  installed: boolean;
  installedAt?: string | null;
  installedBy?: string | null;
  counts: Record<string, number>;
}

export interface DemoInstallationResponse {
  templateKey: string;
  templateVersion: number;
  alreadyInstalled: boolean;
  installedAt?: string | null;
  counts: Record<string, number>;
}

export const demoDataApi = {
  getDemoDataStatus: async (): Promise<DemoDataStatus> => {
    const response = await api.get<ApiResponse<DemoDataStatus>>("/demo-data/status");
    return unwrapResponse(response);
  },

  installDemoData: async (): Promise<DemoInstallationResponse> => {
    const response = await api.post<ApiResponse<DemoInstallationResponse>>("/demo-data/install");
    return unwrapResponse(response);
  },
};
