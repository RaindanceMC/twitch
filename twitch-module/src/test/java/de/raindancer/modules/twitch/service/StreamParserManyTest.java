package de.raindancer.modules.twitch.service;

import de.raindancer.modules.twitch.model.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreamParserManyTest {

    @Test
    @DisplayName("one answer for several channels: who is in it is live, everybody asked and missing is offline")
    void many() {
        String body = "{\"data\":[{\"id\":\"1\",\"user_login\":\"anna\",\"user_name\":\"Anna\",\"type\":\"live\","
                + "\"title\":\"hi\",\"started_at\":\"2026-10-25T10:00:00Z\"}],\"pagination\":{}}";

        Map<String, Observation> seen = StreamParser.readMany(body, List.of("anna", "bert"));

        assertThat(seen.get("anna")).isInstanceOf(Observation.Live.class);
        assertThat(((Observation.Live) seen.get("anna")).stream().title()).isEqualTo("hi");
        assertThat(seen.get("bert")).isEqualTo(Observation.OFFLINE);
    }

    @Test
    @DisplayName("a broken answer is unknown for everybody")
    void broken() {
        assertThat(StreamParser.readMany("{nope", List.of("anna"))).containsEntry("anna", Observation.UNKNOWN);
    }
}
