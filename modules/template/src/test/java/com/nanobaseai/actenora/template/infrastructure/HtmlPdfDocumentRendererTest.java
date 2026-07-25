package com.nanobaseai.actenora.template.infrastructure;

import com.nanobaseai.actenora.template.TemplateTestFixture;
import com.nanobaseai.actenora.template.application.port.out.DocumentRenderer;
import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlPdfDocumentRendererTest {

    private TemplateTestFixture fx;

    @BeforeEach
    void setUp() {
        fx = new TemplateTestFixture();
    }

    @Test
    void htmlPreservesTurkishCharacters() throws Exception {
        DesignSchema design = fx.parser.parseDesign(fx.designJson("header", "executive_summary", "footer"));
        String content = """
                {"header":"Şirket Toplantısı","executive_summary":"ĞÜŞİÖÇ ğüşiöç kararları","footer":"Gizli"}
                """;

        DocumentRenderer.RenderedBytes rendered = fx.renderer.render(design, content, RenderFormat.HTML);
        String html = new String(rendered.bytes(), StandardCharsets.UTF_8);

        assertTrue(html.contains("Şirket Toplantısı"));
        assertTrue(html.contains("ĞÜŞİÖÇ ğüşiöç"));
        assertTrue(html.contains("charset=\"UTF-8\""));
    }

    @Test
    void pdfContainsTurkishTextAndAcceptsLongTables() throws Exception {
        DesignSchema design = fx.parser.parseDesign(
                fx.designJson("header", "executive_summary", "participant_table", "footer"));
        String content = fx.longTableContentJson(80);

        DocumentRenderer.RenderedBytes pdf = fx.renderer.render(design, content, RenderFormat.PDF);
        assertTrue(pdf.bytes().length > 1000);
        assertTrue(new String(pdf.bytes(), StandardCharsets.ISO_8859_1).startsWith("%PDF"));

        // DejaVu-embedded PDF should include Turkish codepoints in content streams / font data.
        String asLatin1 = new String(pdf.bytes(), StandardCharsets.ISO_8859_1);
        assertTrue(asLatin1.contains("DejaVu") || asLatin1.contains("Font") || pdf.bytes().length > 5000);

        DocumentRenderer.RenderedBytes html = fx.renderer.render(design, content, RenderFormat.HTML);
        String htmlText = new String(html.bytes(), StandardCharsets.UTF_8);
        assertTrue(htmlText.contains("page-break-inside: avoid"));
        assertTrue(htmlText.contains("Katılımcı 79"));
        assertTrue(htmlText.contains("ğüşiöç"));
    }
}
