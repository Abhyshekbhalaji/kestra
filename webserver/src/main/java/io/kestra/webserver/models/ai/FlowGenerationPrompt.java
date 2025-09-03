package io.kestra.webserver.models.ai;

import jakarta.validation.constraints.NotNull;

public record FlowGenerationPrompt(@NotNull String conversationId, @NotNull String userPrompt, @NotNull String flowYaml) {}
