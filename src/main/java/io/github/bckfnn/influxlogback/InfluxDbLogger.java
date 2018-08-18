package io.github.bckfnn.influxlogback;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.P6Logger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class InfluxDbLogger implements P6Logger {

    private final Logger logger;

    private AtomicInteger cnt;

    public InfluxDbLogger() {
        this.logger = LoggerFactory.getLogger("influx.p6spy.logger");
        this.cnt = new AtomicInteger(0);
    }

    private static final String INFLUX_TEMPLATE = "sqllog,category=%s connection_id=%s,elapsed=%s,sql=\"%s\" %s\n";

    @Override
    public void logSQL(int connectionId, String now, long elapsed, Category category, String prepared, String sql) {
        final String msg = String.format(INFLUX_TEMPLATE, category.toString(), connectionId, elapsed, sql, Long.parseLong(now) * 1000 + cnt.get());
        if (Category.ERROR.equals(category)) {
            logger.error(msg);
        } else if (Category.WARN.equals(category)) {
            logger.warn(msg);
        } else if (Category.DEBUG.equals(category)) {
            logger.debug(msg);
        } else {
            logger.info(msg);
        }
    }


    @Override
    public void logException(Exception e) {
        logger.info("", e);
    }

    @Override
    public void logText(String text) {
        logger.info(text);
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        if (Category.ERROR.equals(category)) {
            return logger.isErrorEnabled();
        } else if (Category.WARN.equals(category)) {
            return logger.isWarnEnabled();
        } else if (Category.DEBUG.equals(category)) {
            return logger.isDebugEnabled();
        } else {
            return logger.isInfoEnabled();
        }
    }

}
