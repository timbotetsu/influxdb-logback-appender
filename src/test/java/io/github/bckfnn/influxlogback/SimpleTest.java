package io.github.bckfnn.influxlogback;

import ch.qos.logback.classic.LoggerContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTest {

    @Test
    public void singleLog() {
        Logger log = LoggerFactory.getLogger(SimpleTest.class);
        log.info("sqllog,category=statement connection_id=1,elapsed=1,sql=\"SELECT NOW()\" 1534494344337000");

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }
}
