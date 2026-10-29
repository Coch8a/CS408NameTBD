package edu.boisestate.cs408.trenchroster.roster;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Editable roster settings. Ownership always comes from the authenticated session. */
public record RosterRequest(
        @NotBlank(message = "Enter a roster name.")
        @Size(max = 120, message = "Use at most 120 characters.") String rosterName,
        @NotNull(message = "Choose a faction.")
        @Positive(message = "Choose a faction.")
        @JsonDeserialize(using = WholeLong.class) Long factionId,
        @NotNull(message = "Enter a ducat limit.")
        @Positive(message = "Enter a positive whole number.")
        @Max(value = 1000000, message = "Use a limit of at most 1,000,000 ducats.") @JsonDeserialize(using = WholeInteger.class) Integer ducatLimit) {
    public RosterRequest {
        if (rosterName != null) rosterName = rosterName.strip();
    }

    /** Reject fractional JSON values instead of silently rounding them down. */
    public static class WholeLong extends ValueDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return (Long) context.handleUnexpectedToken(Long.class, parser);
            return parser.getLongValue();
        }
    }
    public static class WholeInteger extends ValueDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            return parser.getIntValue();
        }
    }
}
