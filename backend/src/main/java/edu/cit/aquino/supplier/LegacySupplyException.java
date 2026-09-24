package edu.cit.aquino.supplier;

class LegacySupplyException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    LegacySupplyException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    LegacySupplyException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CLIENT_ERROR";
        this.httpStatus = 0;
    }

    public String getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
}
