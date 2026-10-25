package de.raindancer.modules.twitch.service;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.Log;
import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.model.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreatorBookTest {

    private static final UUID ANNA = UUID.randomUUID();

    @TempDir
    Path folder;

    private CreatorBook book() {
        return new CreatorBook(YamlStore.network(folder.resolve("creators.yml")), Log.of("test"));
    }

    @Test
    @DisplayName("everything about a creator is written and read back by another server")
    void roundTrip() {
        CreatorBook one = book();
        assertThat(one.change(ANNA, "Anna", creator -> creator.linked(new ChannelLink(Platform.YOUTUBE, "@anna"))
                .detected("v1", "Hello: \"world\"", 1234L).on(99L))).isTrue();

        Creator read = book().reload().get(ANNA);

        assertThat(read.name()).isEqualTo("Anna");
        assertThat(read.link()).isEqualTo(new ChannelLink(Platform.YOUTUBE, "@anna"));
        assertThat(read.stream()).isEqualTo("v1");
        assertThat(read.title()).isEqualTo("Hello: \"world\"");
        assertThat(read.liveSince()).isEqualTo(1234L);
        assertThat(read.manualSince()).isEqualTo(99L);
    }

    @Test
    @DisplayName("a creator with nothing left — no channel, not live — is forgotten entirely")
    void forgotten() {
        CreatorBook one = book();
        one.change(ANNA, "Anna", creator -> creator.on(5L));
        one.change(ANNA, "Anna", Creator::off);

        assertThat(book().reload()).doesNotContainKey(ANNA);
        assertThat(one.of(ANNA)).isEmpty();
    }

    @Test
    @DisplayName("a channel nobody could read is dropped rather than breaking the book")
    void brokenChannel() throws Exception {
        java.nio.file.Files.writeString(folder.resolve("creators.yml"),
                "creators:\n  " + ANNA + ":\n    name: Anna\n    channel: 'https://example.com/x'\n    manual-since: 7\n");

        Creator read = book().reload().get(ANNA);

        assertThat(read.link()).isNull();
        assertThat(read.manualSince()).isEqualTo(7L);
    }
}
