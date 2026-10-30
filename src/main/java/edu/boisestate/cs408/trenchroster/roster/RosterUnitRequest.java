package edu.boisestate.cs408.trenchroster.roster;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RosterUnitRequest(
        @NotNull(message = "Choose a unit.")
        @Positive(message = "Choose a unit.")
        @JsonDeserialize(using = RosterRequest.WholeLong.class) Long unitId) {}
