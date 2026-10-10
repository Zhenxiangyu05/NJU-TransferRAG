package com.yu.transferrag.protection;

/** Safe public error; never carries provider bodies, queries, addresses or keys. */
public final class ProtectionException extends RuntimeException {
    private final int status; private final String code; private final long retryAfter;
    public ProtectionException(int status,String code,String message,long retryAfter) {
        super(message);this.status=status;this.code=code;this.retryAfter=retryAfter;
    }
    public int status(){return status;} public String code(){return code;} public long retryAfter(){return retryAfter;}
}
