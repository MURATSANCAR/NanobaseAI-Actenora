package com.nanobaseai.actenora.microsoftconnection.infrastructure.auth;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nanobaseai.actenora.microsoftconnection.application.model.AccessToken;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateMicrosoftTokenProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void certificateAuthPostsClientAssertion() throws Exception {
        String certPem = readResource("/certs/test-cert.pem");
        String keyPem = readResource("/certs/test-key-pkcs8.pem");
        PemCredentialsLoader.Material material = PemCredentialsLoader.loadFromPemStrings(certPem, keyPem);
        CertificateCredential credential = new CertificateCredential(
                "tenant-1",
                "client-1",
                wm.baseUrl(),
                material.certificate(),
                material.privateKey(),
                "https://graph.microsoft.com/.default"
        );
        wm.stubFor(post(urlPathMatching("/.*/oauth2/v2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"cert-token\",\"expires_in\":3600}")));

        CertificateMicrosoftTokenProvider provider =
                new CertificateMicrosoftTokenProvider(credential, InstantClock.systemUTC());
        AccessToken token = provider.getAccessToken();
        assertEquals("cert-token", token.value());
        wm.verify(postRequestedFor(urlPathMatching("/.*/oauth2/v2.0/token"))
                .withRequestBody(containing("client_assertion"))
                .withRequestBody(containing("urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer")));
        assertTrue(token.expiresAt().isAfter(Instant.now()));
    }

    private static String readResource(String path) throws Exception {
        try (var in = CertificateMicrosoftTokenProviderTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
