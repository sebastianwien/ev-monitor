package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Which session to merge into the target session, and which side wins on conflicts")
public record MergeSessionRequest(

        @Schema(description = "ID of the session that is merged **into** the target session. "
                + "This session is deleted once the merge succeeds.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @JsonProperty("source_session_id")
        UUID sourceSessionId,

        @Schema(description = "Which session wins when *both* sessions have a value for the same field. "
                + "`false` (default): the target session wins. `true`: the source session wins. "
                + "Fields that are only set on one side are always taken from that side, "
                + "regardless of this flag.",
                defaultValue = "false")
        @JsonProperty("prefer_source")
        Boolean preferSource
) {
    public boolean preferSourceOrDefault() {
        return Boolean.TRUE.equals(preferSource);
    }
}
