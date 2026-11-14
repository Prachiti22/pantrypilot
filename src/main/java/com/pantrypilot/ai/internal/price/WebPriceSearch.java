package com.pantrypilot.ai.internal.price;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.ai.internal.AiProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Looks for menu prices of a dish through a self-hosted SearXNG metasearch
 * instance. Only the dish name and city are sent. Delivery apps are never scraped;
 * prices come from the search result snippets.
 */
@Component
public class WebPriceSearch {

    private static final Logger log = LoggerFactory.getLogger(WebPriceSearch.class);

    private static final Pattern DOLLAR_PRICE = Pattern.compile("\\$\\s?(\\d{1,3}(?:\\.\\d{2})?)(?![\\d,.]*\\d)");
    /** A single restaurant portion outside this range is almost certainly not a menu price. */
    static final BigDecimal MIN_PLAUSIBLE = new BigDecimal("3");
    static final BigDecimal MAX_PLAUSIBLE = new BigDecimal("150");

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WebPriceSearch(AiProperties properties, ObjectMapper mapper) {
        String url = properties.price().searxngUrl();
        this.baseUrl = url == null || url.isBlank() ? null : url.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return baseUrl != null;
    }

    /** Prices found for the dish, possibly empty. Never throws: a failed search just means no samples. */
    public List<BigDecimal> findPrices(String dish, String city) {
        if (baseUrl == null) {
            return List.of();
        }
        String query = "\"" + dish + "\" menu price " + city;
        URI uri = URI.create(baseUrl + "/search?format=json&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("SearXNG answered HTTP {}", response.statusCode());
                return List.of();
            }
            return pricesIn(mapper.readTree(response.body()));
        } catch (IOException e) {
            log.warn("SearXNG search failed: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** Pulls plausible dollar amounts out of the titles and snippets of a SearXNG JSON answer. */
    static List<BigDecimal> pricesIn(JsonNode searchAnswer) {
        List<BigDecimal> prices = new ArrayList<>();
        for (JsonNode result : searchAnswer.path("results")) {
            String text = result.path("title").asText("") + " " + result.path("content").asText("");
            Matcher m = DOLLAR_PRICE.matcher(text);
            while (m.find()) {
                BigDecimal price = new BigDecimal(m.group(1));
                if (price.compareTo(MIN_PLAUSIBLE) >= 0 && price.compareTo(MAX_PLAUSIBLE) <= 0) {
                    prices.add(price);
                }
            }
        }
        return prices;
    }
}
