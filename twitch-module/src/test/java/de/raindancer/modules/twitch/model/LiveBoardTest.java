package de.raindancer.modules.twitch.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Who is live, as the announcements told this server")
class LiveBoardTest {

    private static Map<String, String> live() {
        return Map.of("kind", "live", "name", "Lilly", "title", "Castle", "game", "Minecraft", "started", "1000",
                "stream", "s1");
    }

    @Test
    @DisplayName("nothing is live until an announcement says so, and an end clears it")
    void liveThenEnded() {
        LiveBoard board = new LiveBoard();
        assertThat(board.current()).isEmpty();
        board.heard(live());
        assertThat(board.current()).hasValueSatisfying(now -> {
            assertThat(now.name()).isEqualTo("Lilly");
            assertThat(now.title()).isEqualTo("Castle");
            assertThat(now.game()).isEqualTo("Minecraft");
            assertThat(now.startedAt()).isEqualTo(1000);
        });
        board.heard(Map.of("kind", "ended", "stream", "s1"));
        assertThat(board.current()).isEmpty();
    }

    @Test
    @DisplayName("a staff test never shows as live, and a broken start time becomes unknown")
    void testsAndGarbage() {
        LiveBoard board = new LiveBoard();
        board.heard(Map.of("kind", "live", "name", "x", "test", "true"));
        assertThat(board.current()).isEmpty();
        board.heard(Map.of("kind", "live", "name", "Lilly", "started", "soon"));
        assertThat(board.current()).hasValueSatisfying(now -> assertThat(now.startedAt()).isZero());
    }
}
