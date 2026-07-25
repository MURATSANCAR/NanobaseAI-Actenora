package com.nanobaseai.actenora.template.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesignSchemaValidatorTest {

    @Test
    void rejectsArbitraryJavascriptInProps() {
        DesignComponent bad = new DesignComponent(
                UUID.randomUUID(),
                TemplateComponentType.HEADER,
                0,
                Map.of("onclick", "alert(1)"));

        TemplateDomainException ex = assertThrows(
                TemplateDomainException.class,
                () -> new DesignSchema(1, "A4", List.of(bad)));
        assertEquals("ARBITRARY_JS_FORBIDDEN", ex.code());
    }

    @Test
    void rejectsScriptPayloadInPropValue() {
        DesignComponent bad = new DesignComponent(
                UUID.randomUUID(),
                TemplateComponentType.FOOTER,
                0,
                Map.of("text", "<script>alert(1)</script>"));

        TemplateDomainException ex = assertThrows(
                TemplateDomainException.class,
                () -> new DesignSchema(1, "A4", List.of(bad)));
        assertEquals("ARBITRARY_JS_FORBIDDEN", ex.code());
    }
}
