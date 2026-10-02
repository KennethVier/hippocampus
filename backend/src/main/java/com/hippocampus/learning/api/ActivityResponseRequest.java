package com.hippocampus.learning.api;

import jakarta.validation.constraints.Size;

public record ActivityResponseRequest(
        @Size(max = ActivityResponseRequest.MAX_RESPONSE_TEXT_CHARACTERS) String responseText,
        @Size(max = ActivityResponseRequest.MAX_SELECTED_OPTION_CHARACTERS) String selectedOption) {

    public static final int MAX_RESPONSE_TEXT_CHARACTERS = 8_000;
    public static final int MAX_SELECTED_OPTION_CHARACTERS = 256;
}
