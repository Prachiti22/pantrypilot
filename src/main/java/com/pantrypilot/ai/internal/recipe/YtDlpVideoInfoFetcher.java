package com.pantrypilot.ai.internal.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.shared.RecipeExtractionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads a YouTube video's title, description and captions by running the
 * yt-dlp command-line tool. Nothing is downloaded except that text.
 */
@Component
public class YtDlpVideoInfoFetcher implements VideoInfoFetcher {

    private static final Logger log = LoggerFactory.getLogger(YtDlpVideoInfoFetcher.class);
    private static final int MAX_TRANSCRIPT_CHARS = 12_000;

    private final String executable;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public YtDlpVideoInfoFetcher(AiProperties properties, ObjectMapper mapper) {
        this.executable = properties.links().ytDlpPath();
        this.timeout = properties.links().timeout().multipliedBy(3);
        this.mapper = mapper;
    }

    @Override
    public VideoInfo fetch(String url, boolean withTranscript) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("pantrypilot-yt");
            List<String> command = new ArrayList<>(List.of(
                    executable, "--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings",
                    "--ignore-config"));
            if (withTranscript) {
                command.addAll(List.of(
                        "--no-simulate", "--write-subs", "--write-auto-subs", "--sub-langs", "en.*,en",
                        "--sub-format", "vtt", "--output", workDir.resolve("video.%(ext)s").toString()));
            }
            // "--" ends the options, so a link can never be read as a yt-dlp flag
            command.add("--");
            command.add(url);

            Path stdout = workDir.resolve("info.json");
            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new RecipeExtractionException("Reading that video took too long");
            }
            // yt-dlp prints "null" for private, removed or region-locked videos. The exit code is not
            // checked, because it is also non-zero when only the captions failed to download.
            JsonNode info = Files.size(stdout) > 0 ? mapper.readTree(stdout.toFile()) : null;
            if (info == null || !info.isObject()) {
                throw new RecipeExtractionException("That video could not be read. It may be private or unavailable.");
            }
            String transcript = withTranscript ? readTranscript(workDir) : "";
            return new VideoInfo(info.path("title").asText(""), info.path("description").asText(""), transcript);
        } catch (IOException e) {
            throw new RecipeExtractionException("Video links are not available on this server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecipeExtractionException("Reading that video was interrupted", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String readTranscript(Path workDir) throws IOException {
        try (Stream<Path> files = Files.list(workDir)) {
            Path vtt = files.filter(p -> p.getFileName().toString().endsWith(".vtt")).sorted().findFirst().orElse(null);
            if (vtt == null) {
                return "";
            }
            String text = vttToText(Files.readString(vtt, StandardCharsets.UTF_8));
            return text.length() > MAX_TRANSCRIPT_CHARS ? text.substring(0, MAX_TRANSCRIPT_CHARS) : text;
        }
    }

    /** Strips WebVTT timing lines and tags. Auto-captions repeat each phrase, so duplicates are dropped. */
    static String vttToText(String vtt) {
        Set<String> phrases = new LinkedHashSet<>();
        for (String raw : vtt.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.contains("-->")
                    || line.startsWith("Kind:") || line.startsWith("Language:") || line.startsWith("NOTE")
                    || line.matches("\\d+")) {
                continue;
            }
            String text = line.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").replace("&amp;", "&").strip();
            if (!text.isEmpty()) {
                phrases.add(text);
            }
        }
        return String.join(" ", phrases);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Could not delete temporary file {}", p);
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean temporary folder {}", dir);
        }
    }
}
