package edu.boisestate.cs408.trenchroster.roster;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RosterEquipmentRequest(
        @NotNull @Min(0) @Max(100)
        @JsonDeserialize(using = RosterRequest.WholeInteger.class) Integer quantity) {}
