import { createContext, useContext, type ReactNode } from "react";
import type { ApiClient } from "./types";
import { createApiClient } from "./client";

const ApiContext = createContext<ApiClient | null>(null);

export function ApiProvider({
  client,
  children,
}: {
  client?: ApiClient;
  children: ReactNode;
}) {
  const value = client ?? createApiClient();
  return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>;
}

export function useApi(): ApiClient {
  const ctx = useContext(ApiContext);
  if (!ctx) throw new Error("useApi requires ApiProvider");
  return ctx;
}
