/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bckfnn.influxlogback;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.squareup.tape.QueueFile;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;


/**
 * 
 */
public class InfluxDbAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    // Configuration elements 
    private String url;
    private String auth;
    private String measurement;
    private int connectTimeout = 1000;
    private int readTimeout = 1000;
    private QueueFile queue;
    private int flushIntervalInSeconds = 3;

    private boolean debug = true;
    private String queueDir;
    private final AtomicBoolean drainRunning = new AtomicBoolean(false);
    
    private ArrayList<InfluxTag> tags = new ArrayList<>();
    private ArrayList<InfluxTag> fields = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent logEvent) {
        System.out.println(logEvent);
        logEvent.prepareForDeferredProcessing();
        try {
            queue.add(formatLogEntry(logEvent).getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
            addError("error adding entry", e);
        }
    }

    @Override
    public void start() {
        for (InfluxTag tag : tags) {
            tag.init(context);
        }
        for (InfluxTag tag : fields) {
            tag.init(context);
        }
        
        
        if (queueDir == null) {
            queueDir = System.getProperty("java.io.tmpdir") + File.separator + "influxdb-logback-appender";
        }

        File queueDirectory = new File(queueDir);
        if (queueDirectory.exists()) {
            if (!queueDirectory.canWrite()) {
                addError("The queueDir is noit writeable: "+ queueDirectory.getAbsolutePath());
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
        System.out.println("stop");
        drainQueueAndSend();

        super.stop();
    }
  
    public void drainQueueAndSend() {
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
        debug("Attempting to drain queue " + queue.size());
        if (!queue.isEmpty()) {
            System.out.println("sending");
            try {
                int entriesSend = sendData();
                for (int i = 0; i < entriesSend; i++) {
                    queue.remove();
                }
            } catch (Exception e) {
                debug("Could not send log to influs: ", e);
                debug("Will retry in the next interval");
            }
        }
    }

    
    public void setMeasurement(String measurement) {
        this.measurement = measurement;
    }
    
    public int getFlushIntervalInSeconds() {
        return flushIntervalInSeconds;
    }

    
    public void setUrl(String url) {
        this.url = url;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
        this.flushIntervalInSeconds = flushIntervalInSeconds;
    }
    
    public void setQueueDir(String queueDir) {
        this.queueDir = queueDir;
    }
    


    
    public void addTag(InfluxTag tag) {
        tags.add(tag);
    }

    public void addField(InfluxTag tag) {
        fields.add(tag);
    }

    long lastTime = 0;
    int cnt = 0;
    
    
    private String formatLogEntry(ILoggingEvent logEvent) {
        StringBuilder sb = new StringBuilder(80);
        
        sb.append(measurement);
        
        for (InfluxTag tag : tags) {
            tag.add(sb, logEvent, false);
        }

        sb.append(' ');

        for (InfluxTag tag : fields) {
            tag.add(sb, logEvent, true);
        }
        int last = sb.length() - 1;
        if (sb.charAt(last) == ',') {
          sb.setLength(last);
        }
        sb.append(' ');
        
        long time = logEvent.getTimeStamp();
        if (time == lastTime) {
            cnt++;
            time += cnt;
        } else {
            lastTime = time;
            cnt = 0;
        }
        sb.append(Long.toString(time * 1000 + cnt));
        sb.append('\n');
        return sb.toString();
    }
    

    protected void writeData(String data) {
        if (data.length() == 0) {
            return;
        }
        try {
            final HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            if (auth != null && !auth.isEmpty()) {
                String authEncoded = Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
                con.setRequestProperty("Authorization", "Basic " + authEncoded);
            }
            con.setDoOutput(true);
            con.setConnectTimeout(connectTimeout);
            con.setReadTimeout(readTimeout);
    
            try (OutputStream out = con.getOutputStream()) {
                out.write(data.getBytes("UTF-8"));
            };
    
            int responseCode = con.getResponseCode();
    
            // Check if non 2XX response code.
            if (responseCode / 100 != 2) {
                throw new IOException(
                    "Server returned HTTP response code: " + responseCode + " for URL: " + url + " with content :'"
                        + con.getResponseMessage() + "'");
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    protected int sendData() throws Exception {
        int entriesSend = 0;
        
       
        final HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        if (auth != null && !auth.isEmpty()) {
            String authEncoded = Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
            con.setRequestProperty("Authorization", "Basic " + authEncoded);
        }
        con.setDoOutput(true);
        con.setConnectTimeout(connectTimeout);
        con.setReadTimeout(readTimeout);

        try (OutputStream out = con.getOutputStream()) {
            while (!queue.isEmpty()) {
                byte[] data = queue.peek();
                if (data != null) {
                    out.write(data);
                    entriesSend++;
                }
            }
        };

        int responseCode = con.getResponseCode();

        // Check if non 2XX response code.
        if (responseCode / 100 != 2) {
            throw new IOException(
                "Server returned HTTP response code: " + responseCode + " for URL: " + url + " with content :'"
                    + con.getResponseMessage() + "'");
        }
        return entriesSend;
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
}