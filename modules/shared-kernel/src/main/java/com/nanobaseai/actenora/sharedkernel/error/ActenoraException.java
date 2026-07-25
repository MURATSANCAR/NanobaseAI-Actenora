package com.nanobaseai.actenora.sharedkernel.error;

/**
 * Base unchecked domain/application error. Not a Spring type.
 */
public class ActenoraException extends RuntimeException {

    private final String code;

    public ActenoraException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ActenoraException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
