package com.nanobaseai.actenora.template.application.port.out;

import com.nanobaseai.actenora.template.domain.DesignSchema;
import com.nanobaseai.actenora.template.domain.RenderFormat;

/**
 * HTML/PDF renderer port. Implementations may run in-process or as a dedicated worker.
 */
public interface DocumentRenderer {

    RenderedBytes render(DesignSchema design, String contentJson, RenderFormat format);

    record RenderedBytes(byte[] bytes, String contentType) {
    }
}
