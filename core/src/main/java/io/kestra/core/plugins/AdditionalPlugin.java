package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@io.kestra.core.models.annotations.Plugin
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public abstract class AdditionalPlugin implements Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")
    protected String type;
}
