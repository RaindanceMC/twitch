package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.model.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveModeRuleTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BERT = UUID.randomUUID();
    private static final Duration TWELVE_HOURS = Duration.ofHours(12);
    private static final long NOW = 1_000_000_000L;
    private static final ChannelLink TWITCH = new ChannelLink(Platform.TWITCH, "anna");

    private final LiveModeRule rule = new LiveModeRule();

    @Test
    @DisplayName("live by hand, or because the linked channel is found live")
    void live() {
        assertThat(rule.isLive(Creator.of(ANNA, "Anna").on(NOW - 1000), NOW, TWELVE_HOURS)).isTrue();
        assertThat(rule.isLive(Creator.of(ANNA, "Anna").linked(TWITCH).detected("s1", "t", NOW), NOW, TWELVE_HOURS)).isTrue();
        assertThat(rule.isLive(Creator.of(ANNA, "Anna").linked(TWITCH), NOW, TWELVE_HOURS)).isFalse();
    }

    @Test
    @DisplayName("live by hand runs out after the longest a stream is thought to last")
    void forgotten() {
        Creator on = Creator.of(ANNA, "Anna").on(NOW - Duration.ofHours(13).toMillis());
        assertThat(rule.isLive(on, NOW, TWELVE_HOURS)).isFalse();
    }

    @Test
    @DisplayName("switched off during a found stream: hidden for that stream only, the next one shows again")
    void hidden() {
        Creator streaming = Creator.of(ANNA, "Anna").linked(TWITCH).detected("s1", "t", NOW);
        Creator off = streaming.off();
        assertThat(rule.isLive(off, NOW, TWELVE_HOURS)).isFalse();
        assertThat(rule.isLive(off.detected("s2", "t", NOW), NOW, TWELVE_HOURS)).isTrue();
    }

    @Test
    @DisplayName("who has just gone live, between two pictures of everybody")
    void wentLive() {
        Map<UUID, Creator> before = Map.of(ANNA, Creator.of(ANNA, "Anna").on(NOW));
        Map<UUID, Creator> after = Map.of(ANNA, Creator.of(ANNA, "Anna").on(NOW), BERT, Creator.of(BERT, "Bert").on(NOW));

        assertThat(rule.wentLive(rule.liveNow(before, NOW, TWELVE_HOURS), rule.liveNow(after, NOW, TWELVE_HOURS)))
                .containsExactly(BERT);
        assertThat(rule.liveNow(after, NOW, TWELVE_HOURS)).isEqualTo(Set.of(ANNA, BERT));
    }

    @Test
    @DisplayName("the dot pulses: four shades round and round, each for half a second, the same at the same moment everywhere")
    void pulse() {
        assertThat(LiveModeRule.pulseShade(0)).isEqualTo(LiveModeRule.PULSE.get(0));
        assertThat(LiveModeRule.pulseShade(499)).isEqualTo(LiveModeRule.PULSE.get(0));
        assertThat(LiveModeRule.pulseShade(500)).isEqualTo(LiveModeRule.PULSE.get(1));
        assertThat(LiveModeRule.pulseShade(1500)).isEqualTo(LiveModeRule.PULSE.get(3));
        assertThat(LiveModeRule.pulseShade(2000)).isEqualTo(LiveModeRule.PULSE.get(0));
        assertThat(LiveModeRule.PULSE).hasSize(4).doesNotHaveDuplicates();
    }
}
