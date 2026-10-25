package de.raindancer.modules.twitch.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.identity.NameBadges;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.twitch.TwitchSettings;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.rules.LiveModeRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What every server shows of the creators: the pulsing red dot in front of a live creator's name — chat, tablist,
 * nametag — and one line in chat when somebody goes live. Each server tells only its own players, once for each
 * change it reads, so the network hears it once.
 */
public final class LiveMarks {

    public static final String BADGE = "twitch-live";
    private static final String DOT = "●";

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final Messages messages;
    private final CreatorBook book;
    private final Supplier<TwitchSettings> settings;
    private final LongSupplier clock;
    private final LiveModeRule rule = new LiveModeRule();

    public LiveMarks(Plugin plugin, Server server, LogChannel log, Messages messages, CreatorBook book,
                     Supplier<TwitchSettings> settings, LongSupplier clock) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.messages = messages;
        this.book = book;
        this.settings = settings;
        this.clock = clock;
    }

    public void start() {
        NameBadges.registerPlaced(BADGE, this::badge);
        book.onChange(this::changed);
    }

    public void stop() {
        NameBadges.unregister(BADGE);
    }

    public boolean isLive(UUID player) {
        return settings.get().creatorsEnabled()
                && rule.isLive(book.all().get(player), clock.getAsLong(), settings.get().manualAtMost());
    }

    /** Called for every name drawn, from any thread: reads the picture in memory, nothing more. */
    Optional<Component> badge(UUID player, NameBadges.Place place) {
        if (!isLive(player)) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        if (place != NameBadges.Place.CHAT) {
            return Optional.of(Component.text(DOT, LiveModeRule.pulseShade(now)));
        }
        // A chat line stays as it was sent, so it gets the brightest shade, and is the one place a click works.
        Creator creator = book.all().get(player);
        Component dot = Component.text(DOT, LiveModeRule.PULSE.getFirst());
        Component hover = Component.text("Live", NamedTextColor.RED, TextDecoration.BOLD);
        if (creator.detectedLive() && !creator.title().isBlank()) {
            hover = hover.append(Component.newline()).append(Component.text(clean(creator.title(), 80), NamedTextColor.WHITE));
        }
        if (creator.link() != null) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Click to watch on " + creator.link().platform().spoken(), NamedTextColor.GRAY));
            dot = dot.clickEvent(ClickEvent.openUrl(creator.link().url()));
        }
        return Optional.of(dot.hoverEvent(HoverEvent.showText(hover)));
    }

    private void changed(Map<UUID, Creator> before, Map<UUID, Creator> after) {
        TwitchSettings now = settings.get();
        if (!now.creatorsEnabled() || !now.creatorsAnnounce()) {
            return;
        }
        long at = clock.getAsLong();
        for (UUID fresh : rule.wentLive(rule.liveNow(before, at, now.manualAtMost()), rule.liveNow(after, at, now.manualAtMost()))) {
            announce(after.get(fresh));
        }
    }

    private void announce(Creator creator) {
        String name = clean(creator.name(), 16);
        log.info("Live now: {}.", name);
        for (Player player : server.getOnlinePlayers()) {
            Scheduling.entity(plugin, player, () -> {
                Component line = creator.link() == null ? messages.prefixed("live.announce.plain", "name", name)
                        : messages.prefixed("live.announce.channel", "name", name,
                        "platform", creator.link().platform().spoken(), "channel", creator.link().shown());
                if (creator.link() != null) {
                    line = line.clickEvent(ClickEvent.openUrl(creator.link().url())).hoverEvent(HoverEvent.showText(
                            Component.text("Open " + creator.link().url(), NamedTextColor.GRAY)));
                }
                player.sendMessage(line);
            });
        }
    }

    /** What a stream title or name may put in front of players: one line, no formatting tags, not too long. */
    static String clean(String text, int max) {
        String plain = text == null ? "" : text.replaceAll("[\\p{Cntrl}<>]", " ").strip();
        return plain.length() <= max ? plain : plain.substring(0, max - 1) + "…";
    }
}
