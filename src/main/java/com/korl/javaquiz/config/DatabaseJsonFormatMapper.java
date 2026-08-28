package com.korl.javaquiz.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.hibernate.orm.JsonFormat;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import jakarta.inject.Singleton;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

import java.io.IOException;

/**
 * The mapper Hibernate uses for the {@code jsonb} columns — settings, progress, stats and the
 * quiz session payload.
 *
 * <p>Its own instance rather than the REST one: Quarkus refuses to share that mapper with the
 * database, precisely because retuning it for an endpoint would silently rewrite stored rows.
 * The settings below are the ones the Spring build wrote those rows with, so instants stay
 * ISO-8601 strings and a payload that gains a field still reads back on the old rows.
 *
 * <p>Delegation rather than inheritance because Hibernate's Jackson mapper is final.
 */
@JsonFormat
@PersistenceUnitExtension
@Singleton
public class DatabaseJsonFormatMapper implements FormatMapper {

    private final FormatMapper delegate;

    public DatabaseJsonFormatMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.delegate = new JacksonJsonFormatMapper(mapper);
    }

    @Override
    public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions options) {
        return delegate.fromString(charSequence, javaType, options);
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
        return delegate.toString(value, javaType, options);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return delegate.supportsSourceType(sourceType);
    }

    @Override
    public boolean supportsTargetType(Class<?> targetType) {
        return delegate.supportsTargetType(targetType);
    }

    @Override
    public <T> void writeToTarget(T value, JavaType<T> javaType, Object target, WrapperOptions options)
            throws IOException {
        delegate.writeToTarget(value, javaType, target, options);
    }

    @Override
    public <T> T readFromSource(JavaType<T> javaType, Object source, WrapperOptions options) throws IOException {
        return delegate.readFromSource(javaType, source, options);
    }
}
