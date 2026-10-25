package de.raindancer.modules.twitch.service;

import com.google.gson.JsonParser;
import de.raindancer.modules.twitch.model.Observation;
import de.raindancer.modules.twitch.model.StreamInfo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A channel's {@code /live} page read without an API key: YouTube serves the running live video there (its canonical
 * link a {@code watch?v=}), or the channel page when nothing runs. A scheduled stream also lands on a watch page, so
 * only a video marked live counts — {@code isLiveNow:true} in the broadcast details, or, on the lighter page YouTube
 * serves as well, {@code isLive:true} in the video details. Anything unexpected — a consent wall, a changed page — is
 * {@link Observation#UNKNOWN}, which never ends a stream.
 */
public final class YouTubeParser {

    private static final Pattern CANONICAL = Pattern.compile("<link rel=\"canonical\" href=\"([^\"]+)\"");
    private static final Pattern LIVE_NOW = Pattern.compile("\"liveBroadcastDetails\":\\{[^}]*\"isLiveNow\":true");
    private static final Pattern VIDEO = Pattern.compile("\"videoDetails\":\\{\"videoId\":\"([A-Za-z0-9_-]{6,20})\"");
    private static final Pattern TITLE = Pattern.compile("\"videoDetails\":\\{\"videoId\":\"[^\"]*\",\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern OWNER = Pattern.compile("\"ownerChannelName\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern AUTHOR = Pattern.compile("\"author\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** How far into the video details {@code isLive} is looked for: it comes early, long before the thumbnails. */
    private static final int DETAILS_REACH = 4000;
    private static final Pattern STARTED = Pattern.compile("\"startTimestamp\":\"([^\"]+)\"");

    private YouTubeParser() {
    }

    public static Observation read(String page, String fallbackName) {
        if (page == null || page.isBlank()) {
            return Observation.UNKNOWN;
        }
        Optional<String> canonical = first(CANONICAL, page);
        if (canonical.isEmpty()) {
            return Observation.UNKNOWN;
        }
        if (!canonical.get().contains("/watch?v=") || !LIVE_NOW.matcher(page).find() && !markedLive(page)) {
            return Observation.OFFLINE;
        }
        String video = first(VIDEO, page).orElseGet(() -> canonical.get().substring(canonical.get().indexOf("v=") + 2));
        if (video.isEmpty()) {
            return Observation.UNKNOWN;
        }
        String owner = first(OWNER, page).or(() -> first(AUTHOR, page)).map(YouTubeParser::unescape).orElse(fallbackName);
        return new Observation.Live(new StreamInfo(video, owner, first(TITLE, page).map(YouTubeParser::unescape).orElse(""),
                "", first(STARTED, page).flatMap(YouTubeParser::instant).orElseGet(Instant::now), 0));
    }

    private static boolean markedLive(String page) {
        int details = page.indexOf("\"videoDetails\":{");
        return details >= 0 && page.substring(details, Math.min(page.length(), details + DETAILS_REACH))
                .contains("\"isLive\":true");
    }

    private static Optional<String> first(Pattern pattern, String text) {
        Matcher found = pattern.matcher(text);
        return found.find() ? Optional.of(found.group(1)) : Optional.empty();
    }

    private static String unescape(String json) {
        try {
            return JsonParser.parseString("\"" + json + "\"").getAsString();
        } catch (RuntimeException odd) {
            return json;
        }
    }

    private static Optional<Instant> instant(String text) {
        try {
            return Optional.of(OffsetDateTime.parse(text).toInstant());
        } catch (RuntimeException odd) {
            return Optional.empty();
        }
    }
}
