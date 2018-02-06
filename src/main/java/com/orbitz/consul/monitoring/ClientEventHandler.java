package com.orbitz.consul.monitoring;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import okhttp3.Request;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ClientEventHandler {

    private static final ScheduledExecutorService EVENT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("event-executor-%s").setDaemon(true).build());

    private final String clientName;
    private final ClientEventCallback callback;

    public ClientEventHandler(String clientName, ClientEventCallback callback) {
        this.clientName = clientName;
        this.callback = callback;
    }

    public void httpRequestSuccess(Request request) {
        EVENT_EXECUTOR.submit(() -> callback.onHttpRequestSuccess(clientName, request.method(), request.url().query()));
    }

    public void httpRequestInvalid(Request request, Throwable throwable) {
        EVENT_EXECUTOR.submit(() ->
                callback.onHttpRequestInvalid(clientName, request.method(), request.url().query(), throwable));
    }

    public void httpRequestFailure(Request request, Throwable throwable) {
        EVENT_EXECUTOR.submit(() ->
                callback.onHttpRequestFailure(clientName, request.method(), request.url().query(), throwable));
    }

}
