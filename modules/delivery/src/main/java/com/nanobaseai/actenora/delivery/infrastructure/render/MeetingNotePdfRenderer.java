package com.nanobaseai.actenora.delivery.infrastructure.render;

import com.nanobaseai.actenora.delivery.application.model.MeetingNoteDocument;
import com.nanobaseai.actenora.delivery.domain.DeliveryDomainException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;

/**
 * Renders branded meeting note HTML into PDF bytes.
 */
public final class MeetingNotePdfRenderer {

    public byte[] render(MeetingNoteDocument document) {
        return renderHtml(MeetingNoteBrandedTemplates.pdfHtml(document));
    }

    public byte[] renderHtml(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new DeliveryDomainException("PDF_RENDER_FAILED", "Meeting note PDF render failed: " + ex.getMessage());
        }
    }
}
