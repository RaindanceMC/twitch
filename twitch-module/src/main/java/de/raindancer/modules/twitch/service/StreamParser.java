package de.raindancer.modules.twitch.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.modules.twitch.model.Observation;
import de.raindancer.modules.twitch.model.StreamInfo;

import java.time.Duration;
import java.time.Instant;

/** Twitch's answer to "is this channel live", read without a server — and what a duration sounds like in chat. */
public final class StreamParser {

    private StreamParser() {
    }

    /** {@link Observation#UNKNOWN} for anything that is not a well-formed answer: it must never end a stream. */
    public static Observation read(String body, String fallbackName) {
        if (body == null || body.isBlank()) {
            return Observation.UNKNOWN;
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("data")
                    || !parsed.getAsJsonObject().get("data").isJsonArray()) {
                return Observation.UNKNOWN;
            }
            JsonArray data = parsed.getAsJsonObject().getAsJsonArray("data");
            for (JsonElement each : data) {
                JsonObject stream = each.getAsJsonObject();
                if ("live".equals(text(stream, "type"))) {
                    return live(stream, fallbackName);
                }
            }
            return Observation.OFFLINE;
        } catch (RuntimeException broken) {
            return Observation.UNKNOWN;
        }
    }

    /**
     * One answer for several channels ({@code user_login} asked for each): who is in it is live, every other login
     * asked for is offline; a broken answer is {@link Observation#UNKNOWN} for all of them.
     */
    public static java.util.Map<String, Observation> readMany(String body, java.util.Collection<String> logins) {
        java.util.Map<String, Observation> seen = new java.util.HashMap<>();
        logins.forEach(login -> seen.put(login, Observation.UNKNOWN));
        if (body == null || body.isBlank()) {
            return seen;
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("data")
                    || !parsed.getAsJsonObject().get("data").isJsonArray()) {
                return seen;
            }
            java.util.Map<String, Observation> found = new java.util.HashMap<>();
            logins.forEach(login -> found.put(login, Observation.OFFLINE));
            for (JsonElement each : parsed.getAsJsonObject().getAsJsonArray("data")) {
                JsonObject stream = each.getAsJsonObject();
                String login = text(stream, "user_login").toLowerCase(java.util.Locale.ROOT);
                if (found.containsKey(login) && "live".equals(text(stream, "type"))) {
                    found.put(login, live(stream, login));
                }
            }
            return found;
        } catch (RuntimeException broken) {
            return seen;
        }
    }

    private static Observation live(JsonObject stream, String fallbackName) {
        String id = text(stream, "id");
        String started = text(stream, "started_at");
        if (id.isEmpty() || started.isEmpty()) {
            return Observation.UNKNOWN;
        }
        String name = text(stream, "user_name");
        return new Observation.Live(new StreamInfo(id, name.isEmpty() ? fallbackName : name,
                text(stream, "title"), text(stream, "game_name"), Instant.parse(started),
                stream.has("viewer_count") && stream.get("viewer_count").isJsonPrimitive()
                        ? stream.get("viewer_count").getAsInt() : 0));
    }

    private static String text(JsonObject object, String key) {
        JsonElement found = object.get(key);
        return found != null && found.isJsonPrimitive() ? found.getAsString() : "";
    }

    public static String spoken(Duration lasted) {
        long minutes = lasted.toMinutes();
        if (minutes < 1) {
            return "under a minute";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }
}
