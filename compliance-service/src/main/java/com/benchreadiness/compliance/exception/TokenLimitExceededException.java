package com.benchreadiness.compliance.exception;

public class TokenLimitExceededException extends RuntimeException {
    public TokenLimitExceededException(String message) {
        super(message);
    }
}
