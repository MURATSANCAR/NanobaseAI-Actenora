import {
  EventType,
  InteractionRequiredAuthError,
  PublicClientApplication,
  type AccountInfo,
  type AuthenticationResult,
} from "@azure/msal-browser";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { buildMsalConfig, isMsalAuthEnabled, msalApiScopes, MsalConfigError } from "./msalConfig";
import { setMsalAuthHeaderProvider } from "./authHeaders";

type MsalAuthContextValue = {
  enabled: boolean;
  ready: boolean;
  account: AccountInfo | null;
  configError: string | null;
  login: () => Promise<void>;
  logout: () => Promise<void>;
};

const MsalAuthContext = createContext<MsalAuthContextValue | null>(null);

let pcaSingleton: PublicClientApplication | null = null;

function getMsalInstance(): PublicClientApplication {
  if (pcaSingleton) return pcaSingleton;
  const config = buildMsalConfig();
  if (!config) {
    throw new MsalConfigError("NanobaseAI sign-in is not enabled");
  }
  pcaSingleton = new PublicClientApplication(config);
  return pcaSingleton;
}

async function acquireBearerHeaders(instance: PublicClientApplication): Promise<Record<string, string>> {
  const scopes = msalApiScopes();
  const account = instance.getActiveAccount() ?? instance.getAllAccounts()[0] ?? null;
  if (!account) return {};

  try {
    const result: AuthenticationResult = await instance.acquireTokenSilent({ scopes, account });
    return result.accessToken ? { Authorization: `Bearer ${result.accessToken}` } : {};
  } catch (err) {
    if (err instanceof InteractionRequiredAuthError) {
      await instance.acquireTokenRedirect({ scopes, account });
      return {};
    }
    throw err;
  }
}

export function MsalAuthProvider({ children }: { children: ReactNode }) {
  const enabled = isMsalAuthEnabled();
  const [ready, setReady] = useState(!enabled);
  const [account, setAccount] = useState<AccountInfo | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      setMsalAuthHeaderProvider(null);
      setConfigError(null);
      return;
    }

    let cancelled = false;
    let callbackId: string | null = null;
    let instance: PublicClientApplication;

    try {
      instance = getMsalInstance();
      msalApiScopes();
    } catch (err) {
      const message =
        err instanceof MsalConfigError
          ? err.message
          : "NanobaseAI sign-in is misconfigured";
      setConfigError(message);
      setReady(true);
      setMsalAuthHeaderProvider(null);
      return;
    }

    void (async () => {
      await instance.initialize();
      const redirectResult = await instance.handleRedirectPromise();
      const active =
        redirectResult?.account ?? instance.getActiveAccount() ?? instance.getAllAccounts()[0] ?? null;
      if (active) instance.setActiveAccount(active);
      if (!cancelled) {
        setAccount(active);
        setConfigError(null);
        setReady(true);
      }
    })();

    callbackId = instance.addEventCallback((event) => {
      if (
        event.eventType === EventType.LOGIN_SUCCESS ||
        event.eventType === EventType.ACQUIRE_TOKEN_SUCCESS ||
        event.eventType === EventType.SSO_SILENT_SUCCESS
      ) {
        const payload = event.payload as AuthenticationResult | null;
        const next = payload?.account ?? instance.getActiveAccount();
        if (next) {
          instance.setActiveAccount(next);
          setAccount(next);
        }
      }
      if (event.eventType === EventType.LOGOUT_SUCCESS) {
        setAccount(null);
      }
    });

    setMsalAuthHeaderProvider(() => acquireBearerHeaders(instance));

    return () => {
      cancelled = true;
      if (callbackId) instance.removeEventCallback(callbackId);
      setMsalAuthHeaderProvider(null);
    };
  }, [enabled]);

  const login = useCallback(async () => {
    if (configError) return;
    const instance = getMsalInstance();
    const scopes = msalApiScopes();
    await instance.loginRedirect({ scopes });
  }, [configError]);

  const logout = useCallback(async () => {
    if (configError) return;
    const instance = getMsalInstance();
    await instance.logoutRedirect();
  }, [configError]);

  const value = useMemo<MsalAuthContextValue>(
    () => ({ enabled, ready, account, configError, login, logout }),
    [enabled, ready, account, configError, login, logout],
  );

  return <MsalAuthContext.Provider value={value}>{children}</MsalAuthContext.Provider>;
}

export function useMsalAuth(): MsalAuthContextValue {
  const ctx = useContext(MsalAuthContext);
  if (!ctx) throw new Error("useMsalAuth requires MsalAuthProvider");
  return ctx;
}
