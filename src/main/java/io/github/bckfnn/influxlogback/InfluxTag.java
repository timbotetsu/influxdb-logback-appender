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

import java.util.function.Function;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;

public class InfluxTag {
    private static final Function<String, String> FIELD_ESCAPER = s -> s.replace("\\", "\\\\").replace("\"", "\\\"");
    private static final Function<String, String> KEY_ESCAPER = s -> s.replace(" ", "\\ ").replace(",", "\\,").replace("=", "\\=");
    
    private String name;
    private String pattern;
    
    private String key;
    private PatternLayout patternLayout;
    
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    

    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void init(Context context) {
        System.out.println("init " + this);
        patternLayout = new PatternLayout();
        patternLayout.setContext(context);
        patternLayout.setPattern(getPattern());
        patternLayout.setPostCompileProcessor(null);
        patternLayout.start();
        
        key = KEY_ESCAPER.apply(name);
    }
    
    @Override
    public String toString() {
        return "InfluxTag [name=" + name + ", pattern=" + pattern + "]";
    }
    
    
    public void add(StringBuilder sb, ILoggingEvent logEvent, boolean asField) {
        String value = patternLayout.doLayout(logEvent);
        if (value != null && value.length() > 0) {
            if (asField) {
                String val = FIELD_ESCAPER.apply(value);
                sb.append(key).append("=").append('"').append(val).append('"');
                sb.append(',');
            } else {
                String val = KEY_ESCAPER.apply(value);
                sb.append(',');
                sb.append(key).append("=").append(val);
            }
        }        
    }
}
