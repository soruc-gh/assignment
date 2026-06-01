package com.teya.tinyledger.api;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonDateTimeCustomizer() {
        var module = new SimpleModule();
        module.addSerializer(Instant.class, new MillisecondInstantSerializer());
        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModule(module);
    }

    private static final class MillisecondInstantSerializer extends ValueSerializer<Instant> {

        private static final DateTimeFormatter FORMATTER =
                new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(FORMATTER.format(value));
        }
    }
}
