package io.github.bckfnn.influxlogback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class InfluxDbSQLClassFilter extends Filter<ILoggingEvent> {

    private String logger;
    private Level level;
    private boolean on;

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(level)) {
            return FilterReply.DENY;
        }

        if (!event.getLoggerName().startsWith(logger)) {
            return FilterReply.DENY;
        }

        if (on) {
            return FilterReply.NEUTRAL;
        } else {
            return FilterReply.DENY;
        }
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public void setOn(boolean on) {
        this.on = on;
    }
}
