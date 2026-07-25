package com.nanobaseai.actenora.template.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTest {

    @Test
    void stripsScriptsEventHandlersAndJavascriptUrls() {
        String dirty = """
                <div onclick="alert(1)" class="ok">
                  <script>evil()</script>
                  <a href="javascript:alert(1)">x</a>
                  <img src="https://cdn.example/a.png" onerror="steal()"/>
                  <p>Güvenli metin: ĞÜŞİÖÇ</p>
                </div>
                """;

        String clean = HtmlSanitizer.sanitize(dirty);

        assertFalse(clean.toLowerCase().contains("<script"));
        assertFalse(clean.toLowerCase().contains("onclick"));
        assertFalse(clean.toLowerCase().contains("onerror"));
        assertFalse(clean.toLowerCase().contains("javascript:"));
        assertTrue(clean.contains("Güvenli metin: ĞÜŞİÖÇ"));
        assertTrue(clean.contains("class=\"ok\""));
        assertTrue(clean.contains("cdn.example/a.png"));
    }
}
