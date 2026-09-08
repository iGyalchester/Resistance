package com.resistance.mvc.assistant;

/**
 * Thrown by a listener when the browser has gone away mid-reply (tab
 * closed, Stop pressed, emitter timed out). Unwinds the model stream so
 * no more tokens are bought for nobody; the reply is simply dropped.
 */
public class ClientGoneException extends RuntimeException {

    public ClientGoneException() {
        super("client disconnected");
    }
}
