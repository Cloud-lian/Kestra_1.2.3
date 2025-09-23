package io.kestra.plugin.core.preview;

import io.kestra.core.models.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

/**
 * Interface for plugins to provide file preview rendering capabilities.
 * Plugins can implement this to support preview of specific file formats.
 */
public interface PreviewRenderer extends Plugin {

    /**
     * File extensions this renderer supports (without dot, e.g., "parquet", "csv")
     */
    List<String> supportedExtensions();

    /**
     * Render preview for the given file
     *
     * @param extension file extension
     * @param fileStream input stream of the file
     * @param charset charset for text-based files (optional)
     * @param maxLines maximum number of lines/records to preview
     * @return PreviewResult object containing preview data
     * @throws IOException if file cannot be read or parsed
     */
    PreviewResult render(String extension, InputStream fileStream, Optional<Charset> charset, Integer maxLines) throws IOException;
}