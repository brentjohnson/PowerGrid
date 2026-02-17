package org.powergrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable player domain object.
 */
public record Player(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name
) {}
