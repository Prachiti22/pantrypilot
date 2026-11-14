package com.pantrypilot.ai.internal.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pantrypilot.shared.RecipeExtractionException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

    private final UrlGuard strict = new UrlGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/recipe",
        "http://127.0.0.1/recipe",
        "http://[::1]/recipe",
        "http://10.0.0.5/recipe",
        "http://192.168.1.10/recipe",
        "http://172.16.0.1/recipe",
        "http://169.254.169.254/latest/meta-data",
        "http://100.64.0.1/recipe",
        "http://0.0.0.0/recipe",
    })
    void refusesPrivateAddresses(String url) {
        assertThatThrownBy(() -> strict.check(url)).isInstanceOf(RecipeExtractionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "file:///etc/passwd",
        "ftp://example.com/recipe",
        "javascript:alert(1)",
        "not a link",
        "",
        "http:///nohost",
        "http://user:pass@8.8.8.8/recipe",
        "http://8.8.8.8:8080/recipe",
    })
    void refusesAnythingThatIsNotAPlainWebLink(String url) {
        assertThatThrownBy(() -> strict.check(url)).isInstanceOf(RecipeExtractionException.class);
    }

    @Test
    void acceptsPublicAddresses() {
        assertThat(strict.check("https://8.8.8.8/recipe").getHost()).isEqualTo("8.8.8.8");
        assertThat(strict.check("  http://1.1.1.1:80/x  ").getPort()).isEqualTo(80);
    }

    @Test
    void theRelaxedGuardStillChecksSyntax() {
        UrlGuard relaxed = new UrlGuard(true);
        assertThat(relaxed.check("http://localhost:9000/recipe").getPort()).isEqualTo(9000);
        assertThatThrownBy(() -> relaxed.check("file:///etc/passwd")).isInstanceOf(RecipeExtractionException.class);
    }

    @Test
    void recognisesIpv6UniqueLocalAddresses() throws Exception {
        assertThat(UrlGuard.isPrivate(InetAddress.getByName("fd12:3456:789a::1"))).isTrue();
        assertThat(UrlGuard.isPrivate(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
    }
}
