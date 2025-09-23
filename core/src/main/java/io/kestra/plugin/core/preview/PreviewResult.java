package io.kestra.plugin.core.preview;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewResult {
    public String extension;
    public Type type;
    public Object content;
    public Integer maxLines;

    @JsonInclude
    public boolean truncated = false;

    public enum Type {
        TEXT, LIST, IMAGE, MARKDOWN, PDF
    }
}
