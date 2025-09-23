package io.kestra.plugin.core.preview;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
@Slf4j
public class PreviewRendererRegistry {

    private final Map<String, PreviewRenderer> renderers = new HashMap<>();

    /**
     * Register a preview renderer for specific file extensions
     */
    public void register(PreviewRenderer renderer) {
        for (String extension : renderer.supportedExtensions()) {
            String normalizedExt = extension.toLowerCase();
            if (renderers.containsKey(normalizedExt)) {
                log.warn("Preview renderer for extension '{}' is being overridden by {}",
                    normalizedExt, renderer.getClass().getSimpleName());
            }
            renderers.put(normalizedExt, renderer);
            log.debug("Registered preview renderer for '{}': {}", normalizedExt, renderer.getClass().getSimpleName());
        }
    }

    /**
     * Get preview renderer for given file extension
     */
    public Optional<PreviewRenderer> getRenderer(String extension) {
        return Optional.ofNullable(renderers.get(extension.toLowerCase()));
    }

    /**
     * Check if preview is available for given extension
     */
    public boolean hasRenderer(String extension) {
        return renderers.containsKey(extension.toLowerCase());
    }
}