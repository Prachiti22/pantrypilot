package com.pantrypilot.ai.internal.recipe;

import com.pantrypilot.shared.RecipeExtractionException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Checks a user-supplied link before the server downloads it. Without this a
 * user could make the server fetch pages from its own private network.
 */
public final class UrlGuard {

    private final boolean allowPrivateHosts;

    public UrlGuard(boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    /**
     * @return the parsed link
     * @throws RecipeExtractionException when the link is malformed, not http(s), or points at a private address
     */
    public URI check(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.strip());
        } catch (URISyntaxException e) {
            throw new RecipeExtractionException("That is not a valid link");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new RecipeExtractionException("Only http and https links are supported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getUserInfo() != null) {
            throw new RecipeExtractionException("That is not a valid link");
        }
        if (allowPrivateHosts) {
            return uri;
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new RecipeExtractionException("Links with a custom port are not supported");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new RecipeExtractionException("That website could not be found");
        }
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                throw new RecipeExtractionException("Links to private network addresses are not allowed");
            }
        }
        return uri;
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc; // fc00::/7 unique local
        }
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        return first == 0 || (first == 100 && second >= 64 && second <= 127); // 0.0.0.0/8, carrier-grade NAT
    }
}
