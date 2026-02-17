package org.powergrid.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Singleton Jackson {@link ObjectMapper} configured for the PowerGrid protocol.
 *
 * Usage: {@code JsonMapper.getInstance().writeValueAsString(msg)}
 *
 * Do NOT instantiate ObjectMapper anywhere else — always use this singleton.
 */
public final class JsonMapper {

    private static final ObjectMapper INSTANCE = createMapper();

    private JsonMapper() {}

    public static ObjectMapper getInstance() {
        return INSTANCE;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
