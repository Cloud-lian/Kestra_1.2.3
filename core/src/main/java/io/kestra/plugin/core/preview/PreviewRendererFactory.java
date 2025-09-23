package io.kestra.plugin.core.preview;

import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.plugin.core.preview.PreviewRenderer;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class PreviewRendererFactory {

    private final PluginRegistry pluginRegistry;
    private Map<String, Class<? extends PreviewRenderer>> rendererClasses;

    public PreviewRendererFactory(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Get preview renderer for given file extension
     */
    public Optional<PreviewRenderer> getRenderer(String extension) {
        log.info("Looking for preview renderer for extension: '{}'", extension);

        if (rendererClasses == null) {
            log.info("Renderer classes not initialized, initializing now...");
            initializeRenderers();
        }

        String normalizedExt = extension.toLowerCase();
        Class<? extends PreviewRenderer> rendererClass = rendererClasses.get(normalizedExt);

        log.info("Available extensions: {}", rendererClasses.keySet());
        log.info("Looking for normalized extension: '{}', found class: {}", normalizedExt,
            rendererClass != null ? rendererClass.getName() : "null");

        if (rendererClass == null) {
            log.warn("No preview renderer found for extension '{}'", extension);
            return Optional.empty();
        }

        try {
            PreviewRenderer renderer = rendererClass.getDeclaredConstructor().newInstance();
            log.info("Successfully created preview renderer instance: {}", rendererClass.getSimpleName());
            return Optional.of(renderer);
        } catch (Exception e) {
            log.error("Failed to instantiate preview renderer for extension '{}': {}", extension, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Initialize renderers by discovering all PreviewRenderer plugins
     */
    private void initializeRenderers() {
        rendererClasses = new HashMap<>();

        log.info("Starting to initialize preview renderers...");

        List<RegisteredPlugin> plugins = pluginRegistry.plugins().stream().toList();
        log.info("Found {} registered plugins", plugins.size());

        plugins.forEach(plugin -> {
            List<Class<? extends PreviewRenderer>> renderers = plugin.getPreviewRenderers();
            log.info("Plugin '{}' has {} preview renderers", plugin.name(), renderers.size());
            renderers.forEach(rendererClass ->
                log.info("  - Preview renderer class: {}", rendererClass.getName())
            );
        });

        pluginRegistry.plugins()
            .stream()
            .map(RegisteredPlugin::getPreviewRenderers)
            .flatMap(List::stream)
            .forEach(rendererClass -> {
                try {
                    log.info("Trying to instantiate preview renderer: {}", rendererClass.getName());
                    PreviewRenderer instance = rendererClass.getDeclaredConstructor().newInstance();
                    List<String> extensions = instance.supportedExtensions();
                    log.info("Preview renderer {} supports extensions: {}", rendererClass.getSimpleName(), extensions);

                    for (String extension : extensions) {
                        String normalizedExt = extension.toLowerCase();
                        rendererClasses.put(normalizedExt, rendererClass);
                        log.info("Registered preview renderer for '{}': {}", normalizedExt, rendererClass.getSimpleName());
                    }
                } catch (Exception e) {
                    log.error("Failed to register preview renderer {}: {}", rendererClass.getSimpleName(), e.getMessage(), e);
                }
            });

        log.info("Initialization complete. Registered renderers for extensions: {}", rendererClasses.keySet());
    }

    /**
     * Get all supported extensions
     */
    public List<String> getSupportedExtensions() {
        if (rendererClasses == null) {
            initializeRenderers();
        }
        return List.copyOf(rendererClasses.keySet());
    }
}
