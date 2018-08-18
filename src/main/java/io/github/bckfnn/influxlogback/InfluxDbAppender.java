package io.github.bckfnn.influxlogback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.squareup.tape.QueueFile;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InfluxDbAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // Configuration elements
    private String url;
    private String username;
    private String password;

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private QueueFile queue;
    private int flushIntervalInSeconds = 1;

    private boolean debug = true;
    private String queueDir;
    private final AtomicBoolean drainRunning = new AtomicBoolean(false);

    @Override
    protected void append(ILoggingEvent logEvent) {
        logEvent.prepareForDeferredProcessing();
        try {
            queue.add(logEvent.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            addError("error adding entry", e);
        }
    }

    @Override
    public void start() {
        if (queueDir == null) {
            queueDir = System.getProperty("java.io.tmpdir") + File.separator + "influxdb-logback-appender";
        }

        File queueDirectory = new File(queueDir);
        if (queueDirectory.exists()) {
            if (!queueDirectory.canWrite()) {
                addError("The queueDir is noit writeable: " + queueDirectory.getAbsolutePath());
                return;
            }
        } else {
            if (!queueDirectory.mkdirs()) {
                addError("Creation of queueDir failed: " + queueDirectory.getAbsolutePath());
                return;
            }
        }

        File file = new File(queueDir, "appender.queue");
        try {
            queue = new QueueFile(file);
        } catch (IOException e) {
            addError("Failed to create queue", e);
        }

        context.getScheduledExecutorService().scheduleWithFixedDelay(this::drainQueueAndSend, flushIntervalInSeconds, flushIntervalInSeconds, TimeUnit.SECONDS);

        super.start();
    }

    @Override
    public void stop() {
        drainQueueAndSend();
        super.stop();
    }

    private void drainQueueAndSend() {
        try {
            if (drainRunning.get()) {
                debug("Drain is running so we won't run another one in parallel");
                return;
            } else {
                drainRunning.set(true);
            }
            drainQueue();
        } catch (Exception e) {
            addError("Uncaught error from influx sender", e);
        } finally {
            drainRunning.set(false);
        }
    }

    private void drainQueue() {
        int size = queue.size();
        debug("Attempting to drain queue " + size);
        if (!queue.isEmpty()) {
            debug("Sending .. ");
            try {
                sendData(size);
            } catch (Exception e) {
                debug("Could not send log to influx: ", e);
                debug("Will retry in the next interval");
            }
        }
    }

    private void sendData(int size) throws Exception {
        while (size > 0) {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(username, password));
            byte[] data = queue.peek();
            if (data != null) {
                Request request = builder.post(RequestBody.create(null, data)).build();
                client.newCall(request).execute();
            }
            queue.remove();
            size--;
        }
    }

    private void debug(String message) {
        if (debug) {
            System.out.println(message);
            addInfo("DEBUG: " + message);
        }
    }

    private void debug(String message, Throwable e) {
        if (debug) {
            System.out.println(message);
            addInfo("DEBUG: " + message, e);
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
        this.flushIntervalInSeconds = flushIntervalInSeconds;
    }

    public void setQueueDir(String queueDir) {
        this.queueDir = queueDir;
    }
}