import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { demoDataApi } from "@/lib/api/demo-data";
import { toast } from "sonner";

export const DEMO_DATA_KEYS = {
  all: ["demo-data"] as const,
  status: () => [...DEMO_DATA_KEYS.all, "status"] as const,
};

export function useDemoDataStatus() {
  return useQuery({
    queryKey: DEMO_DATA_KEYS.status(),
    queryFn: () => demoDataApi.getDemoDataStatus(),
    retry: 1,
  });
}

export function useInstallDemoData() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => demoDataApi.installDemoData(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: DEMO_DATA_KEYS.status() });
      if (data.alreadyInstalled) {
        toast.info("Demo workspace is already installed.");
      } else {
        toast.success("Demo workspace ready");
      }
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.message || "Failed to install demo workspace");
    },
  });
}
