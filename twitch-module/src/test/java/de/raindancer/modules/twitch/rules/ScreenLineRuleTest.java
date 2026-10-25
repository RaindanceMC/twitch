package de.raindancer.modules.twitch.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the Twitch screens say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    @Test
    @DisplayName("a live channel shows who, what, since when — and viewers only when they are known")
    void live() {
        String text = rule.live("Lilly", "Building <red>the castle", "Minecraft", Duration.ofMinutes(75), 42);
        assertThat(text).startsWith("<gold><bold>Live on Twitch</bold></gold>")
                .contains("<red>● <white>Lilly")
                .contains("Building \\<red>the castle")
                .contains("<gray>playing <white>Minecraft")
                .contains("<gray>live for <white>1h 15m")
                .contains("<gray>watching <white>42");
        assertThat(rule.live("Lilly", "", "", Duration.ofMinutes(1), 0))
                .doesNotContain("watching").doesNotContain("playing");
        assertThat(rule.live("Lilly", "", "", null, 0)).doesNotContain("live for");
    }

    @Test
    @DisplayName("nobody live says so and names the channel")
    void offline() {
        assertThat(rule.offline("lillyyxoxo_")).isEqualTo(
                "<gold><bold>Live on Twitch</bold></gold>\n<gray>Offline right now\n<dark_gray>twitch.tv/lillyyxoxo_");
    }

    @Test
    @DisplayName("a viewer's own standing: tuned in from the start or later, and minutes near the streamer")
    void you() {
        assertThat(rule.you(false, false, 0, 10)).contains("Nobody is live").doesNotContain("near");
        assertThat(rule.you(true, true, 3, 10)).contains("<green>✔ Tuned in from the start")
                .contains("<gray>near the streamer <white>3</white><gray>/10");
        assertThat(rule.you(true, false, 10, 10)).contains("<gray>Joined after the start")
                .contains("<green>✔ 10/10");
        assertThat(rule.you(true, false, 25, 10)).contains("<green>✔ 10/10");
    }
}
