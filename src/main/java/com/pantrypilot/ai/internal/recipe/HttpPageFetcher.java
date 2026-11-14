package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.shared.RecipeExtractionException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Downloads recipe pages with {@link HttpClient}. Redirects are followed by
 * hand so every hop goes through {@link UrlGuard}, and the download is capped
 * so a huge page cannot exhaust memory.
 */
@Component
public class HttpPageFetcher implements PageFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient http;
    private final UrlGuard guard;
    private final Duration timeout;
    private final int maxBytes;

    public HttpPageFetcher(AiProperties properties) {
        AiProperties.Links config = properties.links();
        this.guard = new UrlGuard(config.allowPrivateHosts());
        this.timeout = config.timeout();
        this.maxBytes = config.maxBytes();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Page fetch(String url) {
        URI uri = guard.check(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response);
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new RecipeExtractionException("That website sent a broken redirect"));
                uri = guard.check(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) {
                close(response);
                throw new RecipeExtractionException("That website answered with an error (HTTP " + status + ")");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            if (!contentType.toLowerCase(Locale.ROOT).contains("html")) {
                close(response);
                throw new RecipeExtractionException("That link is not a web page");
            }
            return new Page(uri.toString(), readCapped(response, contentType));
        }
        throw new RecipeExtractionException("That link redirects too many times");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "Mozilla/5.0 (compatible; PantryPilot recipe reader)")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new RecipeExtractionException("That website could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecipeExtractionException("The download was interrupted", e);
        }
    }

    private String readCapped(HttpResponse<InputStream> response, String contentType) {
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(maxBytes);
            Matcher m = CHARSET.matcher(contentType);
            Charset charset = StandardCharsets.UTF_8;
            if (m.find() && Charset.isSupported(m.group(1))) {
                charset = Charset.forName(m.group(1));
            }
            return new String(bytes, charset);
        } catch (IOException | IllegalArgumentException e) {
            throw new RecipeExtractionException("That page could not be downloaded", e);
        }
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
