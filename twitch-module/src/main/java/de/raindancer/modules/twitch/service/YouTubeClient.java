package de.raindancer.modules.twitch.service;

import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Observation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Looks at a YouTube channel's /live page; see {@link YouTubeParser}. Every failure is {@link Observation#UNKNOWN}. */
public final class YouTubeClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Blocking: call it off the server's threads. */
    public Observation look(ChannelLink channel) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(channel.liveUrl()))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/130.0 Safari/537.36")
                    .header("Accept-Language", "en")
                    // Declines the EU consent wall's non-essential cookies, so the page itself is served.
                    .header("Cookie", "SOCS=CAI")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? YouTubeParser.read(response.body(), channel.handle())
                    : Observation.UNKNOWN;
        } catch (IOException | RuntimeException failed) {
            return Observation.UNKNOWN;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Observation.UNKNOWN;
        }
    }
}
