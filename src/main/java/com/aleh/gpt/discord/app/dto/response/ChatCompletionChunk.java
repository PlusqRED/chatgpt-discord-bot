package com.aleh.gpt.discord.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatCompletionChunk(String id,
                                  String object,
                                  long created,
                                  String model,
                                  @JsonProperty("system_fingerprint") String systemFingerprint,
                                  List<Choice> choices
) {
}