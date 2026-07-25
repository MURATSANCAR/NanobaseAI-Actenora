package com.nanobaseai.actenora.template.domain;

import java.util.Locale;

public enum RenderFormat {
    HTML,
    PDF;

    public String contentType() {
        return this == HTML ? "text/html; charset=UTF-8" : "application/pdf";
    }

    public String fileExtension() {
        return this == HTML ? "html" : "pdf";
    }

    public static RenderFormat fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TemplateDomainException("INVALID_FORMAT", "Render format is required");
        }
        try {
            return RenderFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new TemplateDomainException("INVALID_FORMAT", "Unsupported render format: " + raw);
        }
    }
}
