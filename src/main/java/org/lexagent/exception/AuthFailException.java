package org.lexagent.exception;

import org.lexagent.result.Result;

public class AuthFailException extends RuntimeException {

    private Result result;

    public AuthFailException(String message) {
        super(message);
        this.result=Result.error(message);
    }

    public Result getResult(){
        return result;
    }
}
