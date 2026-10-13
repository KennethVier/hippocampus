package com.hippocampus.learning.api;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Written responses require a request ID; legacy deterministic submissions may omit it. */
public record ActivityResponseRequest(
        @Size(max = ActivityResponseRequest.MAX_RESPONSE_TEXT_CHARACTERS) String responseText,
        @Size(max = ActivityResponseRequest.MAX_SELECTED_OPTION_CHARACTERS) String selectedOption,
        UUID requestId) {

    public static final int MAX_RESPONSE_TEXT_CHARACTERS = 8_000;
    public static final int MAX_SELECTED_OPTION_CHARACTERS = 256;

    public ActivityResponseRequest(String responseText, String selectedOption) {
        this(responseText, selectedOption, null);
    }
}
