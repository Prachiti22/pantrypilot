package com.pantrypilot.ai.internal.ocr;

import com.pantrypilot.ai.internal.AiProperties;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tesseract OCR through Tess4J. Tesseract itself is a native library, so it has
 * to be installed on the machine ({@code brew install tesseract} on macOS).
 *
 * <p>A {@link Tesseract} instance is not thread-safe, so every call makes its own.
 */
@Component
public class Tess4jOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(Tess4jOcrEngine.class);

    /**
     * Page segmentation mode 6: one uniform block of text. It keeps each receipt row on one line.
     * Mode 4 (a column of text) reads the prices as a column of their own and splits "3.49" from its item.
     */
    private static final int PSM_SINGLE_BLOCK = 6;

    private static final List<String> LIBRARY_DIRS = List.of("/opt/homebrew/lib", "/usr/local/lib");
    private static final List<String> DATA_DIRS = List.of(
            "/opt/homebrew/share/tessdata",
            "/usr/local/share/tessdata",
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tessdata");

    private final String language;
    private final Path dataPath;

    public Tess4jOcrEngine(AiProperties properties) {
        AiProperties.Ocr config = properties.ocr();
        this.language = config.language();
        this.dataPath = findDataPath(config.dataPath(), language);
        configureLibraryPath(config.libraryPath());
        if (dataPath == null) {
            log.warn("Tesseract language data for '{}' not found; receipt scanning is disabled. "
                    + "Install Tesseract or set pantrypilot.ai.ocr.data-path.", language);
        }
    }

    @Override
    public String read(BufferedImage image) {
        if (dataPath == null) {
            throw new OcrException("Tesseract language data was not found on this machine", null);
        }
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataPath.toString());
            tesseract.setLanguage(language);
            tesseract.setPageSegMode(PSM_SINGLE_BLOCK);
            tesseract.setVariable("user_defined_dpi", "300");
            String text = tesseract.doOCR(image);
            return text == null ? "" : text;
        } catch (TesseractException | LinkageError e) {
            throw new OcrException("Tesseract could not read the image", e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (dataPath == null) {
            return false;
        }
        try {
            read(new BufferedImage(40, 40, BufferedImage.TYPE_BYTE_GRAY));
            return true;
        } catch (OcrException e) {
            return false;
        }
    }

    private static Path findDataPath(String configured, String language) {
        Stream<String> candidates = Stream.concat(
                Stream.of(configured, System.getenv("TESSDATA_PREFIX")),
                DATA_DIRS.stream());
        return candidates
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .filter(p -> Files.isRegularFile(p.resolve(language + ".traineddata")))
                .findFirst()
                .orElse(null);
    }

    /** JNA looks for the native library in {@code jna.library.path}; Homebrew's folder is not on the default path. */
    private static void configureLibraryPath(String configured) {
        if (System.getProperty("jna.library.path") != null) {
            return;
        }
        Stream.concat(Stream.of(configured), LIBRARY_DIRS.stream())
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .filter(s -> Files.isDirectory(Path.of(s)))
                .findFirst()
                .ifPresent(dir -> System.setProperty("jna.library.path", dir));
    }
}
