package io.kestra.webserver.utils.filepreview;

import io.kestra.plugin.core.preview.FileRender;
import io.kestra.plugin.core.preview.PreviewRenderer;
import io.kestra.plugin.core.preview.PreviewRendererFactory;
import io.kestra.plugin.core.preview.PreviewRendererRegistry;
import io.kestra.plugin.core.preview.PreviewResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Singleton
@Slf4j
public class FileRenderBuilder {
    private static final Charset DEFAULT_FILE_CHARSET = StandardCharsets.UTF_8;

    @Inject
    private PreviewRendererFactory previewRendererFactory;

    public FileRender of(String extension, InputStream filestream, Optional<Charset> charset, Integer maxLine) throws IOException {
        // we check plugin renderers first
        Optional<PreviewRenderer> pluginRenderer = previewRendererFactory.getRenderer(extension);
        if (pluginRenderer.isPresent()) {
            try {
                PreviewResult result = pluginRenderer.get().render(extension, filestream, charset, maxLine);
                return convertToFileRender(result);
            } catch (Exception e) {
                log.warn("Plugin preview renderer failed for extension '{}': {}", extension, e.getMessage());
            }
        }

        if (ImageFileRender.ImageFileExtension.isImageFileExtension(extension)) {
            return new ImageFileRender(extension, filestream, maxLine);
        }

        return switch (extension.toLowerCase()) {
            case "ion" -> new IonFileRender(extension, filestream, maxLine);
            case "md" -> new DefaultFileRender(extension, filestream, DEFAULT_FILE_CHARSET, FileRender.Type.MARKDOWN, maxLine);
            case "pdf" -> new PdfFileRender(extension, filestream, maxLine);
            default -> new DefaultFileRender(extension, filestream, charset.orElse(DEFAULT_FILE_CHARSET), maxLine);
        };
    }

    private FileRender convertToFileRender(PreviewResult result) {
        return new FileRender(result.getExtension(), result.getMaxLines()) {
            {
                this.type = convertType(result.getType());
                this.content = result.getContent();
                this.truncated = result.isTruncated();
            }
        };
    }

    private FileRender.Type convertType(PreviewResult.Type type) {
        return switch (type) {
            case TEXT -> FileRender.Type.TEXT;
            case LIST -> FileRender.Type.LIST;
            case IMAGE -> FileRender.Type.IMAGE;
            case MARKDOWN -> FileRender.Type.MARKDOWN;
            case PDF -> FileRender.Type.PDF;
        };
    }
}