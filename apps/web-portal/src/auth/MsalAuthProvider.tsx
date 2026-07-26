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
import { isMsalAuthEnabled, buildMsalConfig, msalApiScopes } from "./msalConfig";
import { setMsalAuthHeaderProvider } from "./authHeaders";

type MsalAuthContextValue = {
  enabled: boolean;
  ready: boolean;
  account: AccountInfo | null;
  login: () => Promise<void>;
  logout: () => Promise<void>;
};

const MsalAuthContext = createContext<MsalAuthContextValue | null>(null);

let pcaSingleton: PublicClientApplication | null = null;

function getMsalInstance(): PublicClientApplication | null {
  if (pcaSingleton) return pcaSingleton;
  const config = buildMsalConfig();
  if (!config) return null;
  pcaSingleton = new PublicClientApplication(config);
  return pcaSingleton;
}

async function acquireBearerHeaders(instance: PublicClientApplication): Promise<Record<string, string>> {
  const scopes = msalApiScopes();
  if (!scopes.length) return {};

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

  useEffect(() => {
    if (!enabled) {
      setMsalAuthHeaderProvider(null);
      return;
    }

    const instance = getMsalInstance();
    if (!instance) {
      setReady(true);
      return;
    }

    let cancelled = false;

    void (async () => {
      await instance.initialize();
      const redirectResult = await instance.handleRedirectPromise();
      const active = redirectResult?.account ?? instance.getActiveAccount() ?? instance.getAllAccounts()[0] ?? null;
      if (active) instance.setActiveAccount(active);
      if (!cancelled) {
        setAccount(active);
        setReady(true);
      }
    })();

    const callbackId = instance.addEventCallback((event) => {
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
    const instance = getMsalInstance();
    const scopes = msalApiScopes();
    if (!instance || !scopes.length) return;
    await instance.loginRedirect({ scopes });
  }, []);

  const logout = useCallback(async () => {
    const instance = getMsalInstance();
    if (!instance) return;
    await instance.logoutRedirect();
  }, []);

  const value = useMemo<MsalAuthContextValue>(
    () => ({ enabled, ready, account, login, logout }),
    [enabled, ready, account, login, logout],
  );

  return <MsalAuthContext.Provider value={value}>{children}</MsalAuthContext.Provider>;
}

export function useMsalAuth(): MsalAuthContextValue {
  const ctx = useContext(MsalAuthContext);
  if (!ctx) throw new Error("useMsalAuth requires MsalAuthProvider");
  return ctx;
}
