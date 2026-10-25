package de.raindancer.modules.twitch.service;

import de.raindancer.modules.twitch.model.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeParserTest {

    /** The parts of a channel's /live page that matter, as YouTube served them in October 2026. */
    private static final String LIVE = "<html><head><link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=0sfDth4lMLc\">"
            + "</head><script>var ytInitialPlayerResponse = {\"videoDetails\":{\"videoId\":\"0sfDth4lMLc\","
            + "\"title\":\"24/7 lofi \\u0026 chill \\\"beats\\\"\",\"isLive\":true},\"microformat\":{\"playerMicroformatRenderer\":"
            + "{\"ownerChannelName\":\"Lofi Girl\",\"liveBroadcastDetails\":{\"isLiveNow\":true,"
            + "\"startTimestamp\":\"2026-10-20T13:55:15+00:00\"}}}};</script></html>";

    @Test
    @DisplayName("a channel that is live: its video, title, owner and start")
    void live() {
        Observation seen = YouTubeParser.read(LIVE, "@LofiGirl");

        assertThat(seen).isInstanceOf(Observation.Live.class);
        var stream = ((Observation.Live) seen).stream();
        assertThat(stream.id()).isEqualTo("0sfDth4lMLc");
        assertThat(stream.title()).isEqualTo("24/7 lofi & chill \"beats\"");
        assertThat(stream.channel()).isEqualTo("Lofi Girl");
        assertThat(stream.startedAt()).isEqualTo(Instant.parse("2026-10-20T13:55:15Z"));
    }

    @Test
    @DisplayName("the lighter page YouTube also serves — no broadcast details, only the video marked live — is live too")
    void liveLight() {
        String page = "<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=0sfDth4lMLc\">"
                + "\"videoDetails\":{\"videoId\":\"0sfDth4lMLc\",\"title\":\"halloween {lofi}\",\"lengthSeconds\":\"0\","
                + "\"isLive\":true,\"keywords\":[\"lofi\"],\"channelId\":\"UCSJ4gkVC6NrvII8umztf0Ow\",\"author\":\"Lofi Girl\"}";

        Observation seen = YouTubeParser.read(page, "channel/UCSJ4gkVC6NrvII8umztf0Ow");

        assertThat(seen).isInstanceOf(Observation.Live.class);
        assertThat(((Observation.Live) seen).stream().id()).isEqualTo("0sfDth4lMLc");
        assertThat(((Observation.Live) seen).stream().channel()).isEqualTo("Lofi Girl");
    }

    @Test
    @DisplayName("an ended stream kept as a video is not live")
    void replay() {
        String page = "<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=abcdefgh\">"
                + "\"videoDetails\":{\"videoId\":\"abcdefgh\",\"title\":\"old\",\"lengthSeconds\":\"3600\",\"isLiveContent\":true}";
        assertThat(YouTubeParser.read(page, "@x")).isEqualTo(Observation.OFFLINE);
    }

    @Test
    @DisplayName("a channel page (nothing live) is offline")
    void offline() {
        String page = "<html><head><link rel=\"canonical\" href=\"https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA\">"
                + "</head><body>channel</body></html>";
        assertThat(YouTubeParser.read(page, "@MrBeast")).isEqualTo(Observation.OFFLINE);
    }

    @Test
    @DisplayName("a scheduled stream that has not begun is offline, not live")
    void upcoming() {
        String page = LIVE.replace("\"isLiveNow\":true", "\"isLiveNow\":false").replace("\"isLive\":true", "\"isUpcoming\":true");
        assertThat(YouTubeParser.read(page, "@LofiGirl")).isEqualTo(Observation.OFFLINE);
    }

    @Test
    @DisplayName("a consent wall, an empty or odd page says nothing — it must never end a stream")
    void unknown() {
        assertThat(YouTubeParser.read("", "@x")).isEqualTo(Observation.UNKNOWN);
        assertThat(YouTubeParser.read(null, "@x")).isEqualTo(Observation.UNKNOWN);
        assertThat(YouTubeParser.read("<html>Before you continue to YouTube</html>", "@x")).isEqualTo(Observation.UNKNOWN);
    }
}
