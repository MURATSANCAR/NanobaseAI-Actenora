package com.nanobaseai.actenora.security.aiprocessing;

import com.nanobaseai.actenora.aiprocessing.application.port.LocalModelProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.LlamaCppProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.MockLocalProvider;
import com.nanobaseai.actenora.aiprocessing.infrastructure.llm.VllmProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProviderFactoryTest {

    @Test
    void mockProviderIsAllowedOutsideProduction() {
        LocalModelProvider provider = LocalProviderFactory.create(properties("mock", null), false);
        assertInstanceOf(MockLocalProvider.class, provider);
    }

    @Test
    void mockProviderIsRefusedOnProduction() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> LocalProviderFactory.create(properties("mock", null), true));
        assertTrue(ex.getMessage().contains("offline"));
        assertTrue(ex.getMessage().contains("production"));
    }

    @Test
    void vllmProviderIsBuiltForLoopbackEndpoint() {
        LocalModelProvider provider =
                LocalProviderFactory.create(properties("vllm", URI.create("http://127.0.0.1:8000")), true);
        assertInstanceOf(VllmProvider.class, provider);
        assertEquals("vllm", provider.capabilities().providerKind());
    }

    @Test
    void llamaCppProviderIsBuiltForPrivateNetworkEndpoint() {
        LocalModelProvider provider =
                LocalProviderFactory.create(properties("llamacpp", URI.create("http://10.1.2.3:8080")), true);
        assertInstanceOf(LlamaCppProvider.class, provider);
    }

    @Test
    void cloudEndpointIsRefused() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> LocalProviderFactory.create(
                        properties("openai", URI.create("https://api.openai.com")), false));
        assertTrue(ex.getMessage().contains("local or private"));
    }

    private static LocalProviderProperties properties(String kind, URI baseUrl) {
        LocalProviderProperties properties = new LocalProviderProperties();
        properties.setKind(kind);
        if (baseUrl != null) {
            properties.setBaseUrl(baseUrl);
        }
        properties.setServedModelIds(Set.of("qwen-local"));
        return properties;
    }
}
