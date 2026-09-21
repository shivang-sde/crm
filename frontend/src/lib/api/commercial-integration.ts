import api from "./api";
import { unwrapResponse, unwrapListResponse } from "./api-utils";
import type { ApiResponse } from "@/types/auth";

export interface CommercialKeyStatus {
  configured: boolean;
  keyPrefix: string | null;
}

export interface CommercialRotateResponse {
  apiKey: string;
  prefix: string;
  message: string;
}

export interface CommercialDocument {
  id: string;
  externalType: string;
  externalId: string;
  accountId: string | null;
  contactId: string | null;
  externalUpdatedAt: string | null;
  pdfUrl: string | null;
}

export interface CommercialDocumentListParams {
  accountId?: string;
  contactId?: string;
  externalType?: "QUOTATION" | "INVOICE";
  page?: number;
  size?: number;
}

export interface CommercialDocumentListMeta {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export const commercialIntegrationApi = {
  getStatus: async (): Promise<CommercialKeyStatus> => {
    const response = await api.get<ApiResponse<CommercialKeyStatus>>(
      "/admin/commercial/api-keys"
    );
    return unwrapResponse(response);
  },
  rotate: async (): Promise<CommercialRotateResponse> => {
    const response = await api.post<ApiResponse<CommercialRotateResponse>>(
      "/admin/commercial/api-keys/rotate"
    );
    return unwrapResponse(response);
  },
  listDocuments: async (params: CommercialDocumentListParams) => {
    const response = await api.get<ApiResponse<CommercialDocument[]>>(
      "/commercial-documents",
      { params }
    );
    const { data, meta } = unwrapListResponse<CommercialDocument, Partial<CommercialDocumentListMeta>>(response);
    return {
      data,
      meta: {
        page: meta.page ?? 0,
        size: meta.size ?? 20,
        total: meta.total ?? data.length,
        totalPages: meta.totalPages ?? 1,
      },
    };
  },
};
