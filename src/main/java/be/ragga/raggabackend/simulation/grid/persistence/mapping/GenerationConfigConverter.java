package be.ragga.raggabackend.simulation.grid.persistence.mapping;

import be.ragga.raggabackend.simulation.grid.generation.GenerationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists the full {@link GenerationConfig} snapshot as a JSON string, so a
 * stored City records exactly which tuning knobs produced it without the
 * schema growing a column per knob. GenerationConfig is a record, which Jackson
 * serializes/deserializes natively via its canonical constructor - the same
 * constructor re-runs its validation on load, so a corrupt row fails loudly.
 */
@Converter
public class GenerationConfigConverter implements AttributeConverter<GenerationConfig, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(GenerationConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize GenerationConfig", e);
        }
    }

    @Override
    public GenerationConfig convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, GenerationConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize GenerationConfig", e);
        }
    }
}
