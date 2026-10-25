package de.raindancer.modules.twitch.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.twitch.TwitchSettings;
import de.raindancer.modules.twitch.model.LiveBoard;
import de.raindancer.modules.twitch.model.StreamInfo;
import de.raindancer.modules.twitch.model.ViewerTracker;
import de.raindancer.modules.twitch.rules.AnnouncementRule;
import de.raindancer.modules.twitch.rules.ScreenLineRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The Twitch screens for Core's {@code /screen}: who is live, and each viewer's own standing. */
public final class TwitchScreens {

    public static final List<String> KINDS = List.of("twitch-live", "twitch-you");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final LiveBoard board;
    private final ViewerTracker tracker;
    private final Supplier<TwitchPoller> poller;
    private final Supplier<TwitchSettings> settings;
    private final LongSupplier clock;
    private final ScreenLineRule rule = new ScreenLineRule();
    private final AnnouncementRule clean = new AnnouncementRule();

    public TwitchScreens(LiveBoard board, ViewerTracker tracker, Supplier<TwitchPoller> poller,
                         Supplier<TwitchSettings> settings, LongSupplier clock) {
        this.board = board;
        this.tracker = tracker;
        this.poller = poller;
        this.settings = settings;
        this.clock = clock;
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.everyone("twitch-live", "Live on Twitch",
                "Whether the channel is live: what it plays, how long, how many watch.", Material.AMETHYST_CLUSTER, this::live));
        screens.register(owner, ScreenKind.personal("twitch-you", "Your stream",
                "Everybody's own standing for the On Stream advancements while the channel is live.",
                Material.NOTE_BLOCK, viewer -> you(viewer)));
    }

    private Component live() {
        TwitchSettings now = settings.get();
        // The server that asks Twitch knows the viewer count; the others only what the announcement said.
        TwitchPoller asking = poller.get();
        Optional<StreamInfo> seen = asking == null ? Optional.empty() : asking.tracker().running();
        if (seen.isPresent()) {
            StreamInfo stream = seen.get();
            return MINI.deserialize(rule.live(clean.clean(stream.channel(), 40), clean.clean(stream.title(), 80),
                    clean.clean(stream.game(), 40), since(stream.startedAt() == null ? 0 : stream.startedAt().toEpochMilli()),
                    stream.viewers()));
        }
        return board.current().map(live -> MINI.deserialize(rule.live(clean.clean(live.name(), 40),
                        clean.clean(live.title(), 80), clean.clean(live.game(), 40), since(live.startedAt()), 0)))
                .orElseGet(() -> MINI.deserialize(rule.offline(now.channelClean())));
    }

    private Component you(ScreenKind.Viewer viewer) {
        return MINI.deserialize(rule.you(tracker.isLive(), tracker.fromStart(viewer.id()),
                tracker.minutesNear(viewer.id()), ViewerTracker.CAUGHT_MINUTES));
    }

    private Duration since(long startedAt) {
        return startedAt <= 0 ? null : Duration.ofMillis(Math.max(0, clock.getAsLong() - startedAt));
    }
}
