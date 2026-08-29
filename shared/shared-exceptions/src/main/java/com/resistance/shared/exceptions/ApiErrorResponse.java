package com.resistance.shared.exceptions;

/**
 * Error payload returned by REST controllers, generalized from the
 * per-service *ErrorResponse classes of the original course projects.
 */
public class ApiErrorResponse {

    private int status;
    private String message;
    private long timeStamp;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(int status, String message, long timeStamp) {
        this.status = status;
        this.message = message;
        this.timeStamp = timeStamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
}
