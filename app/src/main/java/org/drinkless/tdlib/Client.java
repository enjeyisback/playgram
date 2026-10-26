package org.drinkless.tdlib;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/* JADX INFO: loaded from: classes2.dex */
public final class Client {
    private final int nativeClientId;
    private static final ConcurrentHashMap<Integer, ExceptionHandler> defaultExceptionHandlers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Handler> updateHandlers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Handler> handlers = new ConcurrentHashMap<>();
    private static final AtomicLong currentQueryId = new AtomicLong();
    private static final AtomicLong clientCount = new AtomicLong();
    private static final ResponseReceiver responseReceiver = new ResponseReceiver(0);

    static {
        try {
            System.loadLibrary("tdjni");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public interface ExceptionHandler {
        void onException(Throwable th);
    }

    public static class ExecutionException extends Exception {
        public final TdApi.Error error;

        public ExecutionException(TdApi.Error error) {
            super(error.code + ": " + error.message);
            this.error = error;
        }
    }

    public static class Handler {
        final ExceptionHandler exceptionHandler;
        final ResultHandler resultHandler;

        public Handler(ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
            this.resultHandler = resultHandler;
            this.exceptionHandler = exceptionHandler;
        }
    }

    public interface LogMessageHandler {
        void onLogMessage(int i8, String str);
    }

    public static class ResponseReceiver implements Runnable {
        private static final int MAX_EVENTS = 1000;
        private final int[] clientIds;
        private final long[] eventIds;
        private final TdApi.Object[] events;
        public boolean isRun;

        public /* synthetic */ ResponseReceiver(int i8) {
            this();
        }

        private void processResult(int i8, long j9, TdApi.Object object) {
            boolean z9 = j9 == 0 && (object instanceof TdApi.UpdateAuthorizationState) && (((TdApi.UpdateAuthorizationState) object).authorizationState instanceof TdApi.AuthorizationStateClosed);
            Handler handler = (Handler) (j9 == 0 ? Client.updateHandlers.get(Integer.valueOf(i8)) : Client.handlers.remove(Long.valueOf(j9)));
            if (handler != null) {
                try {
                    handler.resultHandler.onResult(object);
                } catch (Throwable th) {
                    ExceptionHandler exceptionHandler = handler.exceptionHandler;
                    if (exceptionHandler == null) {
                        exceptionHandler = (ExceptionHandler) Client.defaultExceptionHandlers.get(Integer.valueOf(i8));
                    }
                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.onException(th);
                        } catch (Throwable unused) {
                        }
                    }
                }
            }
            if (z9) {
                Client.updateHandlers.remove(Integer.valueOf(i8));
                Client.defaultExceptionHandlers.remove(Integer.valueOf(i8));
                Client.clientCount.decrementAndGet();
            }
        }

        @Override // java.lang.Runnable
        public void run() {
            while (true) {
                int iE = Client.e(this.clientIds, this.eventIds, this.events);
                for (int i8 = 0; i8 < iE; i8++) {
                    processResult(this.clientIds[i8], this.eventIds[i8], this.events[i8]);
                    this.events[i8] = null;
                }
            }
        }

        private ResponseReceiver() {
            this.isRun = false;
            this.clientIds = new int[MAX_EVENTS];
            this.eventIds = new long[MAX_EVENTS];
            this.events = new TdApi.Object[MAX_EVENTS];
        }
    }

    public interface ResultHandler {
        void onResult(TdApi.Object object);
    }

    private Client(ResultHandler resultHandler, ExceptionHandler exceptionHandler, ExceptionHandler exceptionHandler2) {
        clientCount.incrementAndGet();
        int iCreateNativeClient = createNativeClient();
        this.nativeClientId = iCreateNativeClient;
        if (resultHandler != null) {
            updateHandlers.put(Integer.valueOf(iCreateNativeClient), new Handler(resultHandler, exceptionHandler));
        }
        if (exceptionHandler2 != null) {
            defaultExceptionHandlers.put(Integer.valueOf(iCreateNativeClient), exceptionHandler2);
        }
        send(new TdApi.GetOption("version"), null, null);
    }

    public static Client create(ResultHandler resultHandler, ExceptionHandler exceptionHandler, ExceptionHandler exceptionHandler2) {
        Client client = new Client(resultHandler, exceptionHandler, exceptionHandler2);
        ResponseReceiver responseReceiver2 = responseReceiver;
        synchronized (responseReceiver2) {
            try {
                if (!responseReceiver2.isRun) {
                    responseReceiver2.isRun = true;
                    Thread thread = new Thread(responseReceiver2, "TDLib thread");
                    thread.setDaemon(true);
                    thread.start();
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return client;
    }

    private static native int createNativeClient();

    public static /* bridge */ /* synthetic */ int e(int[] iArr, long[] jArr, TdApi.Object[] objectArr) {
        return nativeClientReceive(iArr, jArr, objectArr, 100000.0d);
    }

    @SuppressWarnings("unchecked")
    public static <T extends TdApi.Object> T execute(TdApi.Function<T> function) throws ExecutionException {
        TdApi.Object result = nativeClientExecute(function);
        if (result instanceof TdApi.Error) {
            throw new ExecutionException((TdApi.Error) result);
        }
        return (T) result;
    }

    private static native TdApi.Object nativeClientExecute(TdApi.Function<?> function);

    private static native int nativeClientReceive(int[] iArr, long[] jArr, TdApi.Object[] objectArr, double d9);

    private static native void nativeClientSend(int i8, long j9, TdApi.Function<?> function);

    private static native void nativeClientSetLogMessageHandler(int i8, LogMessageHandler logMessageHandler);

    public static void setLogMessageHandler(int i8, LogMessageHandler logMessageHandler) {
        nativeClientSetLogMessageHandler(i8, logMessageHandler);
    }

    public void send(TdApi.Function<?> function, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        long jIncrementAndGet = currentQueryId.incrementAndGet();
        if (resultHandler != null) {
            handlers.put(Long.valueOf(jIncrementAndGet), new Handler(resultHandler, exceptionHandler));
        }
        nativeClientSend(this.nativeClientId, jIncrementAndGet, function);
    }

    public void send(TdApi.Function<?> function, ResultHandler resultHandler) {
        send(function, resultHandler, null);
    }
}
