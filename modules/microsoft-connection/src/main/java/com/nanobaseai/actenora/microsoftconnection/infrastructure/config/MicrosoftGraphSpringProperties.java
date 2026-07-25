package com.nanobaseai.actenora.microsoftconnection.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties(prefix = "actenora.microsoft-graph")
public class MicrosoftGraphSpringProperties {

    private boolean enabled = false;
    private String graphBaseUrl = "https://graph.microsoft.com";
    private String authorityHost = "https://login.microsoftonline.com";
    private String tenantId;
    private String clientId;
    private String scope = "https://graph.microsoft.com/.default";
    /** CERTIFICATE (production) or CLIENT_SECRET (local/test only). */
    private String authMode = "CERTIFICATE";
    private String clientSecret;
    private String certificatePemPath;
    private String privateKeyPemPath;
    private Duration subscriptionRenewBefore = Duration.ofHours(6);
    private Duration subscriptionRenewWindow = Duration.ofHours(48);
    private boolean workersEnabled = true;

    public MicrosoftGraphProperties toProperties() {
        MicrosoftGraphProperties.AuthMode mode =
                "CLIENT_SECRET".equalsIgnoreCase(authMode)
                        ? MicrosoftGraphProperties.AuthMode.CLIENT_SECRET
                        : MicrosoftGraphProperties.AuthMode.CERTIFICATE;
        return new MicrosoftGraphProperties(
                URI.create(graphBaseUrl),
                URI.create(authorityHost),
                tenantId,
                clientId,
                scope,
                mode,
                Optional.ofNullable(clientSecret),
                Optional.ofNullable(certificatePemPath),
                Optional.ofNullable(privateKeyPemPath),
                subscriptionRenewBefore,
                subscriptionRenewWindow,
                workersEnabled
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGraphBaseUrl() {
        return graphBaseUrl;
    }

    public void setGraphBaseUrl(String graphBaseUrl) {
        this.graphBaseUrl = graphBaseUrl;
    }

    public String getAuthorityHost() {
        return authorityHost;
    }

    public void setAuthorityHost(String authorityHost) {
        this.authorityHost = authorityHost;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getCertificatePemPath() {
        return certificatePemPath;
    }

    public void setCertificatePemPath(String certificatePemPath) {
        this.certificatePemPath = certificatePemPath;
    }

    public String getPrivateKeyPemPath() {
        return privateKeyPemPath;
    }

    public void setPrivateKeyPemPath(String privateKeyPemPath) {
        this.privateKeyPemPath = privateKeyPemPath;
    }

    public Duration getSubscriptionRenewBefore() {
        return subscriptionRenewBefore;
    }

    public void setSubscriptionRenewBefore(Duration subscriptionRenewBefore) {
        this.subscriptionRenewBefore = subscriptionRenewBefore;
    }

    public Duration getSubscriptionRenewWindow() {
        return subscriptionRenewWindow;
    }

    public void setSubscriptionRenewWindow(Duration subscriptionRenewWindow) {
        this.subscriptionRenewWindow = subscriptionRenewWindow;
    }

    public boolean isWorkersEnabled() {
        return workersEnabled;
    }

    public void setWorkersEnabled(boolean workersEnabled) {
        this.workersEnabled = workersEnabled;
    }
}
