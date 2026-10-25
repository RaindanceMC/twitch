package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelLinkRuleTest {

    private final ChannelLinkRule rule = new ChannelLinkRule();

    @ParameterizedTest
    @ValueSource(strings = {"https://www.twitch.tv/Lillyyxoxo_", "twitch.tv/lillyyxoxo_", "http://m.twitch.tv/lillyyxoxo_/videos",
            " <https://twitch.tv/lillyyxoxo_?sr=a> "})
    @DisplayName("a Twitch channel however it is pasted")
    void twitch(String pasted) {
        ChannelLink link = rule.parse(pasted).orElseThrow();

        assertThat(link.platform()).isEqualTo(Platform.TWITCH);
        assertThat(link.handle()).isEqualTo("lillyyxoxo_");
        assertThat(link.url()).isEqualTo("https://www.twitch.tv/lillyyxoxo_");
        assertThat(link.shown()).isEqualTo("twitch.tv/lillyyxoxo_");
    }

    @Test
    @DisplayName("a YouTube handle, channel id or custom name; the live page is the channel's /live")
    void youtube() {
        ChannelLink handle = rule.parse("https://www.youtube.com/@LofiGirl/featured").orElseThrow();
        assertThat(handle.platform()).isEqualTo(Platform.YOUTUBE);
        assertThat(handle.handle()).isEqualTo("@LofiGirl");
        assertThat(handle.url()).isEqualTo("https://www.youtube.com/@LofiGirl");
        assertThat(handle.liveUrl()).isEqualTo("https://www.youtube.com/@LofiGirl/live");

        assertThat(rule.parse("youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA").orElseThrow().handle())
                .isEqualTo("channel/UCX6OQ3DkcsbYNE6H8uQQuVA");
        assertThat(rule.parse("m.youtube.com/c/SomeName").orElseThrow().handle()).isEqualTo("c/SomeName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "lilly", "https://example.com/lilly", "twitch.tv/", "twitch.tv/directory",
            "twitch.tv/name-with-dash", "youtube.com/watch?v=abc", "youtube.com/channel/notAnId", "youtu.be/abc",
            "javascript:alert(1)", "https://twitch.tv.evil.com/x", "youtube.com/@a"})
    @DisplayName("anything that is not a channel page is refused")
    void refused(String pasted) {
        assertThat(rule.parse(pasted)).isEmpty();
    }

    @Test
    @DisplayName("a stored channel reads back as the same link")
    void roundTrip() {
        ChannelLink link = rule.parse("youtube.com/@LofiGirl").orElseThrow();
        assertThat(rule.parse(link.url())).contains(link);
    }
}
