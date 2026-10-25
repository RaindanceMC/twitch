package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.Observation;
import de.raindancer.modules.twitch.model.StreamInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionRuleTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-10-25T10:00:00Z");

    private static Observation live(String id) {
        return new Observation.Live(new StreamInfo(id, "Anna", "title " + id, "", START, 0));
    }

    @Test
    @DisplayName("a stream is told once; looking again while it runs tells nothing")
    void once() {
        DetectionRule rule = new DetectionRule(Map.of());

        assertThat(rule.observe(ANNA, live("s1"), 3)).containsInstanceOf(DetectionRule.Change.Live.class);
        assertThat(rule.observe(ANNA, live("s1"), 3)).isEmpty();
    }

    @Test
    @DisplayName("over only after enough offline looks in a row; a failed look counts for nothing")
    void flicker() {
        DetectionRule rule = new DetectionRule(Map.of());
        rule.observe(ANNA, live("s1"), 3);

        assertThat(rule.observe(ANNA, Observation.OFFLINE, 3)).isEmpty();
        assertThat(rule.observe(ANNA, Observation.UNKNOWN, 3)).isEmpty();
        assertThat(rule.observe(ANNA, Observation.OFFLINE, 3)).isEmpty();
        assertThat(rule.observe(ANNA, live("s1"), 3)).isEmpty();
        rule.observe(ANNA, Observation.OFFLINE, 3);
        rule.observe(ANNA, Observation.OFFLINE, 3);
        assertThat(rule.observe(ANNA, Observation.OFFLINE, 3)).containsInstanceOf(DetectionRule.Change.Over.class);
        assertThat(rule.observe(ANNA, Observation.OFFLINE, 3)).isEmpty();
    }

    @Test
    @DisplayName("a server taking over the watch knows what the last one wrote down: no second 'live'")
    void takeover() {
        DetectionRule rule = new DetectionRule(Map.of(ANNA, "s1"));

        assertThat(rule.observe(ANNA, live("s1"), 3)).isEmpty();
        assertThat(rule.observe(ANNA, live("s2"), 3)).containsInstanceOf(DetectionRule.Change.Live.class);
    }

    @Test
    @DisplayName("somebody offline that nobody thought live is nothing to tell")
    void quiet() {
        assertThat(new DetectionRule(Map.of()).observe(ANNA, Observation.OFFLINE, 1)).isEmpty();
    }
}
