package de.raindancer.modules.twitch.command;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.Log;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.twitch.TwitchSettings;
import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.model.Platform;
import de.raindancer.modules.twitch.service.CreatorBook;
import de.raindancer.modules.twitch.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveCommandTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BERT = UUID.randomUUID();
    private static final long NOW = 1_000_000L;

    @TempDir
    Path folder;

    private Messages messages;
    private CreatorBook book;
    private LiveCommand command;
    private TwitchSettings settings = TwitchSettings.DEFAULTS;

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        book = new CreatorBook(YamlStore.network(folder.resolve("creators.yml")), Log.of("test"));
        LiveCommand.Services services = new LiveCommand.Services(messages, book, () -> settings, () -> NOW,
                name -> name.equalsIgnoreCase("bert") ? Optional.of(new LiveCommand.Known(BERT, "Bert")) : Optional.empty(),
                () -> true, Runnable::run);
        command = new LiveCommand(() -> services);
    }

    private Player player(UUID id, String name, boolean creator, boolean staff) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.hasPermission(PermissionNodes.LIVE)).thenReturn(creator);
        when(player.hasPermission(PermissionNodes.ADMIN)).thenReturn(staff);
        return player;
    }

    private void run(CommandSender sender, String... args) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        command.execute(source, args);
    }

    @Test
    @DisplayName("/live toggles a creator on and off")
    void toggle() {
        Player anna = player(ANNA, "Anna", true, false);

        run(anna);
        assertThat(book.of(ANNA)).map(Creator::manualSince).contains(NOW);
        verify(messages).send(eq(anna), eq("live.on"), any(Object[].class));

        run(anna);
        assertThat(book.of(ANNA)).isEmpty();
        verify(messages).send(eq(anna), eq("live.off"), any(Object[].class));
    }

    @Test
    @DisplayName("somebody who is not a creator cannot go live")
    void notCreator() {
        Player bert = player(BERT, "Bert", false, false);

        run(bert, "on");

        assertThat(book.all()).isEmpty();
        verify(messages).send(eq(bert), eq("live.not-creator"), any(Object[].class));
    }

    @Test
    @DisplayName("a creator links their channel from its url; a bad url is refused and nothing stored")
    void link() {
        Player anna = player(ANNA, "Anna", true, false);

        run(anna, "link", "notachannel");
        assertThat(book.of(ANNA)).isEmpty();
        verify(messages).send(eq(anna), eq("live.link.bad"), any(Object[].class));

        run(anna, "link", "https://www.twitch.tv/Anna_Streams");
        assertThat(book.of(ANNA)).map(Creator::link).contains(new ChannelLink(Platform.TWITCH, "anna_streams"));

        run(anna, "unlink");
        assertThat(book.of(ANNA)).isEmpty();
    }

    @Test
    @DisplayName("staff link and switch a creator by name; a creator cannot do it for somebody else")
    void forSomebody() {
        Player staff = player(ANNA, "Anna", false, true);
        run(staff, "link", "bert", "youtube.com/@bert");
        run(staff, "on", "bert");
        assertThat(book.of(BERT)).map(Creator::link).contains(new ChannelLink(Platform.YOUTUBE, "@bert"));
        assertThat(book.of(BERT)).map(Creator::manualSince).contains(NOW);
        assertThat(book.of(ANNA)).isEmpty();

        Player creator = player(UUID.randomUUID(), "Cleo", true, false);
        run(creator, "off", "bert");
        assertThat(book.of(BERT)).map(Creator::manualSince).contains(NOW);
        verify(messages).send(eq(creator), eq("live.not-allowed"), any(Object[].class));
    }

    @Test
    @DisplayName("off while the linked channel is found live hides this stream and says so")
    void offDuringStream() {
        Player anna = player(ANNA, "Anna", true, false);
        book.change(ANNA, "Anna", c -> c.linked(new ChannelLink(Platform.TWITCH, "anna")).detected("s1", "t", NOW));

        run(anna, "off");

        assertThat(book.of(ANNA)).map(Creator::hiddenStream).contains("s1");
        verify(messages).send(eq(anna), eq("live.off-hidden"), any(Object[].class));
    }

    @Test
    @DisplayName("switched off by the owner: nobody goes live and nothing is stored")
    void disabled() {
        settings = new TwitchSettings(true, "x", 60, 3, 15, true, "", 32, false, 60, true, 12);
        Player anna = player(ANNA, "Anna", true, false);

        run(anna, "on");

        assertThat(book.all()).isEmpty();
        verify(messages).send(eq(anna), eq("live.disabled"), any(Object[].class));
        verify(messages, never()).send(eq(anna), eq("live.on"), any(Object[].class));
    }
}
