package com.hashwhale.core.service;

public class LtvLimitExceededException extends RuntimeException {

    public LtvLimitExceededException(String message) {
        super(message);
    }
}
