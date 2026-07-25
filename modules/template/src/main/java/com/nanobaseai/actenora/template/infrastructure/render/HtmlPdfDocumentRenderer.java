package com.nanobaseai.actenora.template.infrastructure.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.template.application.port.out.DocumentRenderer;
import com.nanobaseai.actenora.template.domain.DesignComponent;
import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.HtmlSanitizer;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.TemplateComponentType;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Renders design + content into sanitized HTML, optionally converting to PDF
 * with embedded DejaVu Sans for Turkish character coverage and CSS page-break support.
 */
public final class HtmlPdfDocumentRenderer implements DocumentRenderer {

    private final ObjectMapper mapper;

    public HtmlPdfDocumentRenderer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public RenderedBytes render(DesignSchema design, String contentJson, RenderFormat format) {
        String html = buildHtml(design, contentJson);
        String sanitized = HtmlSanitizer.sanitize(html);
        if (format == RenderFormat.HTML) {
            byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
            return new RenderedBytes(bytes, format.contentType());
        }
        return new RenderedBytes(toPdf(sanitized), format.contentType());
    }

    String buildHtml(DesignSchema design, String contentJson) {
        JsonNode content = readContent(contentJson);
        StringBuilder body = new StringBuilder();
        body.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<!DOCTYPE html>")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"tr\"><head>")
                .append("<meta charset=\"UTF-8\" />")
                .append("<style>")
                .append("@page { size: ").append(escape(design.pageSize())).append("; margin: 18mm; }")
                .append("body { font-family: 'DejaVu Sans', sans-serif; font-size: 11pt; color: #111; }")
                .append("h1,h2,h3 { margin: 0 0 8px 0; }")
                .append("table { width: 100%; border-collapse: collapse; page-break-inside: auto; }")
                .append("tr { page-break-inside: avoid; page-break-after: auto; }")
                .append("thead { display: table-header-group; }")
                .append("th,td { border: 1px solid #ccc; padding: 6px; text-align: left; vertical-align: top; }")
                .append(".section { margin-bottom: 16px; page-break-inside: avoid; }")
                .append(".page-break { page-break-before: always; }")
                .append(".footer,.page-number { font-size: 9pt; color: #555; }")
                .append("</style></head><body>");

        for (DesignComponent component : design.components()) {
            body.append(renderComponent(component, content));
        }
        body.append("</body></html>");
        return body.toString();
    }

    private String renderComponent(DesignComponent component, JsonNode content) {
        TemplateComponentType type = component.type();
        String title = component.props().getOrDefault("title", defaultTitle(type));
        String bindingKey = type.wireName();
        JsonNode section = content.path(bindingKey);
        if (section.isMissingNode()) {
            section = content.path(bindingKey.replace('_', '-'));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"section component-").append(bindingKey).append("\">");
        switch (type) {
            case LOGO -> sb.append("<div class=\"logo\">")
                    .append(imgOrText(section, component.props()))
                    .append("</div>");
            case HEADER -> sb.append("<div class=\"header\"><h1>").append(text(section, title)).append("</h1></div>");
            case METADATA -> sb.append("<h2>").append(escape(title)).append("</h2>")
                    .append(metadataList(section));
            case PARTICIPANT_TABLE, DECISIONS, ACTIONS, RISKS, OPEN_QUESTIONS, COMMITMENTS, AGENDA ->
                    sb.append("<h2>").append(escape(title)).append("</h2>").append(table(section, type));
            case EXECUTIVE_SUMMARY -> sb.append("<h2>").append(escape(title)).append("</h2>")
                    .append("<p>").append(text(section, "")).append("</p>");
            case SIGNATURE -> sb.append("<h2>").append(escape(title)).append("</h2>")
                    .append("<p>").append(text(section, component.props().getOrDefault("label", ""))).append("</p>");
            case FOOTER -> sb.append("<div class=\"footer\">")
                    .append(text(section, component.props().getOrDefault("text", ""))).append("</div>");
            case CONFIDENTIALITY -> sb.append("<p class=\"confidentiality\"><strong>")
                    .append(text(section, component.props().getOrDefault("text", "Gizli"))).append("</strong></p>");
            case PAGE_NUMBER -> sb.append("<div class=\"page-number\">")
                    .append(escape(component.props().getOrDefault("format", "Sayfa")))
                    .append("</div>");
            default -> sb.append("<div>").append(text(section, "")).append("</div>");
        }
        sb.append("</section>");
        return sb.toString();
    }

    private String table(JsonNode section, TemplateComponentType type) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        String[] headers = headersFor(type);
        for (String header : headers) {
            sb.append("<th>").append(escape(header)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        if (section.isArray()) {
            for (JsonNode row : section) {
                sb.append("<tr>");
                for (String header : headers) {
                    String key = headerKey(header);
                    sb.append("<td>").append(escape(cellValue(row, key))).append("</td>");
                }
                sb.append("</tr>");
            }
        } else if (section.isTextual()) {
            sb.append("<tr><td colspan=\"").append(headers.length).append("\">")
                    .append(escape(section.asText())).append("</td></tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String[] headersFor(TemplateComponentType type) {
        return switch (type) {
            case PARTICIPANT_TABLE -> new String[] {"Ad", "Rol", "E-posta"};
            case AGENDA -> new String[] {"Madde", "Süre"};
            case DECISIONS -> new String[] {"Karar", "Sahip"};
            case ACTIONS -> new String[] {"Aksiyon", "Sorumlu", "Termin"};
            case RISKS -> new String[] {"Risk", "Etki", "Azaltma"};
            case OPEN_QUESTIONS -> new String[] {"Soru", "Sahip"};
            case COMMITMENTS -> new String[] {"Taahhüt", "Sahip", "Termin"};
            default -> new String[] {"Değer"};
        };
    }

    private static String headerKey(String header) {
        return switch (header) {
            case "Ad" -> "name";
            case "Rol" -> "role";
            case "E-posta" -> "email";
            case "Madde" -> "item";
            case "Süre" -> "duration";
            case "Karar" -> "decision";
            case "Sahip" -> "owner";
            case "Aksiyon" -> "action";
            case "Sorumlu" -> "assignee";
            case "Termin" -> "due";
            case "Risk" -> "risk";
            case "Etki" -> "impact";
            case "Azaltma" -> "mitigation";
            case "Soru" -> "question";
            case "Taahhüt" -> "commitment";
            default -> "value";
        };
    }

    private static String cellValue(JsonNode row, String key) {
        if (row.has(key)) {
            return row.path(key).asText("");
        }
        // fallback: first textual field
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        if (fields.hasNext()) {
            return fields.next().getValue().asText("");
        }
        return row.asText("");
    }

    private String metadataList(JsonNode section) {
        StringBuilder sb = new StringBuilder("<ul>");
        if (section.isObject()) {
            section.fields().forEachRemaining(e ->
                    sb.append("<li><strong>").append(escape(e.getKey())).append(":</strong> ")
                            .append(escape(e.getValue().asText(""))).append("</li>"));
        } else {
            sb.append("<li>").append(escape(section.asText(""))).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private String imgOrText(JsonNode section, Map<String, String> props) {
        String src = section.path("src").asText(props.getOrDefault("src", ""));
        String alt = section.path("alt").asText(props.getOrDefault("alt", "logo"));
        if (src.isBlank()) {
            return "<span>" + escape(alt) + "</span>";
        }
        return "<img src=\"" + escape(src) + "\" alt=\"" + escape(alt) + "\" />";
    }

    private String text(JsonNode section, String fallback) {
        if (section == null || section.isMissingNode() || section.isNull()) {
            return escape(fallback);
        }
        if (section.isTextual() || section.isNumber() || section.isBoolean()) {
            return escape(section.asText());
        }
        if (section.has("text")) {
            return escape(section.path("text").asText(fallback));
        }
        if (section.has("value")) {
            return escape(section.path("value").asText(fallback));
        }
        return escape(fallback);
    }

    private JsonNode readContent(String contentJson) {
        try {
            if (contentJson == null || contentJson.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(contentJson);
        } catch (Exception ex) {
            throw new TemplateDomainException("INVALID_CONTENT", "Content JSON is invalid", ex);
        }
    }

    private byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            if (HtmlPdfDocumentRenderer.class.getResource("/fonts/DejaVuSans.ttf") != null) {
                builder.useFont(
                        () -> HtmlPdfDocumentRenderer.class.getResourceAsStream("/fonts/DejaVuSans.ttf"),
                        "DejaVu Sans");
            }
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new TemplateDomainException("PDF_RENDER_FAILED", "PDF rendering failed: " + ex.getMessage(), ex);
        }
    }

    private static String defaultTitle(TemplateComponentType type) {
        return switch (type) {
            case LOGO -> "Logo";
            case HEADER -> "Toplantı Notu";
            case METADATA -> "Künye";
            case PARTICIPANT_TABLE -> "Katılımcılar";
            case EXECUTIVE_SUMMARY -> "Yönetici Özeti";
            case AGENDA -> "Gündem";
            case DECISIONS -> "Kararlar";
            case ACTIONS -> "Aksiyonlar";
            case RISKS -> "Riskler";
            case OPEN_QUESTIONS -> "Açık Sorular";
            case COMMITMENTS -> "Taahhütler";
            case SIGNATURE -> "İmza";
            case FOOTER -> "Alt Bilgi";
            case CONFIDENTIALITY -> "Gizlilik";
            case PAGE_NUMBER -> "Sayfa";
        };
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
