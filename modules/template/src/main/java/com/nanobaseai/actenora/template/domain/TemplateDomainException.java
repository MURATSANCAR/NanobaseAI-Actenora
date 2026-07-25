package com.nanobaseai.actenora.template.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public class TemplateDomainException extends ActenoraException {

    public TemplateDomainException(String code, String message) {
        super(code, message);
    }

    public TemplateDomainException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
