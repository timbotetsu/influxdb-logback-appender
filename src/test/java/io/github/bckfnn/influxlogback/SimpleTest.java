package io.github.bckfnn.influxlogback;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

public class SimpleTest {

 
    @Test
    public void singleLog() {
        Logger log = LoggerFactory.getLogger(SimpleTest.class) ;
        log.info("test");
        
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }
}
