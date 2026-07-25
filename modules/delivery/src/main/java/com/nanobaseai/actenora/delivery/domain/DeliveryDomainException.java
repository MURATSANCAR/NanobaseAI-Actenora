package com.nanobaseai.actenora.delivery.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public class DeliveryDomainException extends ActenoraException {

    public DeliveryDomainException(String code, String message) {
        super(code, message);
    }

    public DeliveryDomainException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
