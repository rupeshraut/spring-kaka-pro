package com.company.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Generic Message Model for unknown message types
 * Used as fallback when specific type cannot be determined
 */
@Data
@Builder
@Jacksonized
public class GenericMessage {
    
    @JsonProperty("type")
    private final String type;
    
    @JsonProperty("data")
    private final Map<String, Object> data;
    
    @JsonProperty("metadata")
    private final Map<String, Object> metadata;
    
    @JsonProperty("schema_version")
    private final String schemaVersion;
}
