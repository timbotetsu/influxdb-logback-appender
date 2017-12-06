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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;


/**
 * 
 */
public class InfluxDbAppender extends AppenderBase<ILoggingEvent> {
    // Configuration elements 
    private String url;
    private String auth;
    private String measurement;
    private int connectTimeout = 1000;
    private int readTimeout = 1000;
    
    private int flushIntervalInSeconds = 3;
    int queueSize = 256;
    
    BlockingQueue<ILoggingEvent> blockingQueue = new ArrayBlockingQueue<ILoggingEvent>(queueSize);
    
    private ArrayList<InfluxTag> tags = new ArrayList<>();
    private ArrayList<InfluxTag> fields = new ArrayList<>();

    // Runtime
    private ScheduledExecutorService scheduledExecutor;

    
    @Override
    protected void append(ILoggingEvent logEvent) {
        logEvent.prepareForDeferredProcessing();
        if (!blockingQueue.offer(logEvent)) {
            System.err.println("queue full");
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
        
        // SCHEDULER
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("logback-influx-appender");
                thread.setDaemon(true);
                return thread;
            }
        };
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduledExecutor.scheduleWithFixedDelay(new InfluxExporter(), flushIntervalInSeconds, flushIntervalInSeconds, TimeUnit.SECONDS);
        
        super.start();

    }

    @Override
    public void stop() {
        System.out.println("stop");
        scheduledExecutor.shutdown();

        processLogEntries();

        super.stop();
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
    
    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    
    public void addTag(InfluxTag tag) {
        tags.add(tag);
    }

    public void addField(InfluxTag tag) {
        fields.add(tag);
    }

    long lastTime = 0;
    int cnt = 0;
    
    public void processLogEntries() {
        int lines = 0;
        
        StringBuilder sb = new StringBuilder();
        while (blockingQueue.size() > 0) {
            System.out.println("xxx");
            ILoggingEvent logEvent = blockingQueue.poll();
            
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
            
            if (lines > 1000) {
                writeData(sb.toString());
                lines = 0;
            }
 
        }
        writeData(sb.toString());
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

    public class InfluxExporter implements Runnable {
        @Override
        public void run() {
            try {
                processLogEntries();
            } catch (Exception e) {
                addWarn("Exception processing log entries", e);
            }
        }
    }
}