package io.kestra.plugin.core.dashboard.chart.mardown.sources;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FlowDescription.class, name = "FlowDescription"),
    @JsonSubTypes.Type(value = Text.class, name = "Text")
})
public class MarkdownSource {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")
    private String type;
}
