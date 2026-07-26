import { createContext, useContext, useMemo, type ReactNode } from "react";
import type { ApiClient } from "./types";
import { createApiClient, type ApiMode } from "./client";

type ApiContextValue = {
  client: ApiClient;
  mode: ApiMode;
};

const ApiContext = createContext<ApiContextValue | null>(null);

function resolveMode(client?: ApiClient, explicit?: ApiMode): ApiMode {
  if (explicit) return explicit;
  const meta = (typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined;
  return (meta?.VITE_API_MODE as ApiMode | undefined) ?? "mock";
}

export function ApiProvider({
  client,
  mode,
  children,
}: {
  client?: ApiClient;
  mode?: ApiMode;
  children: ReactNode;
}) {
  const value = useMemo<ApiContextValue>(() => {
    const resolvedMode = resolveMode(client, mode);
    return {
      client: client ?? createApiClient({ mode: resolvedMode }),
      mode: resolvedMode,
    };
  }, [client, mode]);

  return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>;
}

export function useApi(): ApiClient {
  const ctx = useContext(ApiContext);
  if (!ctx) throw new Error("useApi requires ApiProvider");
  return ctx.client;
}

export function useApiMode(): ApiMode {
  const ctx = useContext(ApiContext);
  if (!ctx) throw new Error("useApiMode requires ApiProvider");
  return ctx.mode;
}
