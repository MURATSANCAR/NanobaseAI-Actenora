package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.ActenoraProfiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Ensures portal SPA auth mode matches backend security auth mode.
 * Strict production requires MSAL portal + Entra API (no {@code X-Mock-*}).
 */
@Component
public class PortalAuthModeGuard implements ApplicationRunner {

    private final Environment environment;
    private final String portalAuthMode;
    private final String securityAuthMode;

    public PortalAuthModeGuard(
            Environment environment,
            @Value("${actenora.portal.auth.mode:mock}") String portalAuthMode,
            @Value("${actenora.security.auth.mode:mock}") String securityAuthMode
    ) {
        this.environment = environment;
        this.portalAuthMode = portalAuthMode;
        this.securityAuthMode = securityAuthMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        String portal = normalize(portalAuthMode);
        String security = normalize(securityAuthMode);

        if ("msal".equals(portal) && !"entra".equals(security)) {
            throw new IllegalStateException(
                    "Refusing to start: actenora.portal.auth.mode=msal requires actenora.security.auth.mode=entra "
                            + "(SPA Bearer tokens; X-Mock-* headers must not be used)");
        }

        if (ActenoraProfiles.isStrictProduction(environment)) {
            if (!"msal".equals(portal)) {
                throw new IllegalStateException(
                        "Refusing to start: actenora.portal.auth.mode must be msal on production "
                                + "(got '" + portalAuthMode + "')");
            }
            if (!"entra".equals(security)) {
                throw new IllegalStateException(
                        "Refusing to start: actenora.security.auth.mode must be entra on production "
                                + "(got '" + securityAuthMode + "')");
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
