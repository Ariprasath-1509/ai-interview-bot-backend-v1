package com.benchreadiness.ops.compliance.exception;

public class TokenLimitExceededException extends RuntimeException {
    public TokenLimitExceededException(String message) { super(message); }
}
