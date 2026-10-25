package de.raindancer.modules.twitch.model;

import java.util.Map;
import java.util.Optional;

/**
 * Who is live, as far as the announcements that reached this server told — so every server of the network knows,
 * not only the one that asks Twitch. A server that starts mid-stream learns nothing until the next announcement.
 */
public final class LiveBoard {

    public record LiveNow(String name, String title, String game, long startedAt) {
    }

    private volatile LiveNow current;

    public void heard(Map<String, String> data) {
        if ("true".equals(data.get("test"))) {
            return;
        }
        if ("live".equals(data.get("kind"))) {
            current = new LiveNow(data.getOrDefault("name", ""), data.getOrDefault("title", ""),
                    data.getOrDefault("game", ""), millis(data.get("started")));
        } else if ("ended".equals(data.get("kind"))) {
            current = null;
        }
    }

    public Optional<LiveNow> current() {
        return Optional.ofNullable(current);
    }

    private static long millis(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException unreadable) {
            return 0;
        }
    }
}
