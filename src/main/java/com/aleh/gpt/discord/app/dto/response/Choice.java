package com.aleh.gpt.discord.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Choice(int index, Delta delta, Object logprobs, @JsonProperty("finish_reason") String finishReason) {
}
