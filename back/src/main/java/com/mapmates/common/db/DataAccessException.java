package com.mapmates.common.db;

/** SQLException을 감싸는 런타임 예외. Service·Controller가 checked 예외에 묶이지 않게 한다. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
