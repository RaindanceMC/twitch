package de.raindancer.modules.twitch.command;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.twitch.TwitchSettings;
import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.model.Platform;
import de.raindancer.modules.twitch.rules.ChannelLinkRule;
import de.raindancer.modules.twitch.rules.LiveModeRule;
import de.raindancer.modules.twitch.service.CreatorBook;
import de.raindancer.modules.twitch.service.StreamParser;
import de.raindancer.modules.twitch.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@code /live}: a creator switches their live mode on and off and links their channel; staff do the same for a
 * creator by name. {@code /live list} is for everybody.
 */
public final class LiveCommand implements ITwitchCommand {

    /** A player found by name, online or known to the server. */
    public record Known(UUID id, String name) {
    }

    /**
     * @param twitchLogin whether Twitch can be asked at all — without a login a linked Twitch channel is not watched
     * @param background  where the document is written: never the server's thread
     */
    public record Services(Messages messages, CreatorBook book, Supplier<TwitchSettings> settings, LongSupplier clock,
                           Function<String, Optional<Known>> players, BooleanSupplier twitchLogin, Executor background) {
    }

    private static final List<String> ACTIONS = List.of("on", "off", "link", "unlink", "status", "list");

    private final Supplier<Services> services;
    private final ChannelLinkRule links = new ChannelLinkRule();
    private final LiveModeRule rule = new LiveModeRule();

    public LiveCommand(Supplier<Services> services) {
        this.services = services;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        Services live = services.get();
        CommandSender sender = source.getSender();
        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            list(live, sender);
            return;
        }
        if (!live.settings().get().creatorsEnabled()) {
            live.messages().send(sender, "live.disabled");
            return;
        }
        // Somebody else is named when there is one argument more than the action needs for oneself.
        int ownArgs = action.equals("link") ? 2 : 1;
        Optional<Known> target;
        if (args.length > ownArgs) {
            if (!sender.hasPermission(PermissionNodes.ADMIN)) {
                live.messages().send(sender, "live.not-allowed");
                return;
            }
            target = live.players().apply(args[1]);
            if (target.isEmpty()) {
                live.messages().send(sender, "live.unknown-player", "name", args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            if (!player.hasPermission(PermissionNodes.LIVE) && !player.hasPermission(PermissionNodes.ADMIN)) {
                live.messages().send(sender, "live.not-creator");
                return;
            }
            target = Optional.of(new Known(player.getUniqueId(), player.getName()));
        } else {
            live.messages().send(sender, "live.usage");
            return;
        }
        Known who = target.get();
        boolean self = sender instanceof Player player && player.getUniqueId().equals(who.id());
        switch (action) {
            case "toggle" -> live.background().execute(() -> {
                boolean isLive = rule.isLive(live.book().of(who.id()).orElse(null), live.clock().getAsLong(),
                        live.settings().get().manualAtMost());
                if (isLive) {
                    off(live, sender, who, self);
                } else {
                    on(live, sender, who, self);
                }
            });
            case "on" -> live.background().execute(() -> on(live, sender, who, self));
            case "off" -> live.background().execute(() -> off(live, sender, who, self));
            case "link" -> {
                String pasted = args[args.length - 1];
                Optional<ChannelLink> link = args.length < 2 ? Optional.empty() : links.parse(pasted);
                if (link.isEmpty()) {
                    live.messages().send(sender, "live.link.bad");
                    return;
                }
                live.background().execute(() -> link(live, sender, who, self, link.get()));
            }
            case "unlink" -> live.background().execute(() -> {
                if (live.book().of(who.id()).map(Creator::link).isEmpty()) {
                    live.messages().send(sender, "live.unlink.none", "name", who.name());
                } else if (live.book().change(who.id(), who.name(), Creator::unlinked)) {
                    live.messages().send(sender, self ? "live.unlink.done" : "live.unlink.done-other", "name", who.name());
                } else {
                    live.messages().send(sender, "live.failed");
                }
            });
            case "status" -> status(live, sender, who);
            default -> live.messages().send(sender, "live.usage");
        }
    }

    private void on(Services live, CommandSender sender, Known who, boolean self) {
        if (live.book().change(who.id(), who.name(), creator -> creator.on(live.clock().getAsLong()))) {
            live.messages().send(sender, self ? "live.on" : "live.on-other", "name", who.name(),
                    "hours", String.valueOf(live.settings().get().manualAtMost().toHours()));
        } else {
            live.messages().send(sender, "live.failed");
        }
    }

    private void off(Services live, CommandSender sender, Known who, boolean self) {
        boolean streaming = live.book().of(who.id()).map(Creator::detectedLive).orElse(false);
        if (live.book().change(who.id(), who.name(), Creator::off)) {
            live.messages().send(sender, streaming ? (self ? "live.off-hidden" : "live.off-hidden-other")
                    : (self ? "live.off" : "live.off-other"), "name", who.name());
        } else {
            live.messages().send(sender, "live.failed");
        }
    }

    private void link(Services live, CommandSender sender, Known who, boolean self, ChannelLink link) {
        if (!live.book().change(who.id(), who.name(), creator -> creator.linked(link))) {
            live.messages().send(sender, "live.failed");
            return;
        }
        live.messages().send(sender, self ? "live.link.done" : "live.link.done-other", "name", who.name(),
                "channel", link.shown(), "platform", link.platform().spoken());
        if (link.platform() == Platform.TWITCH && !live.twitchLogin().getAsBoolean()) {
            live.messages().send(sender, "live.link.twitch-unwatched");
        }
    }

    private void status(Services live, CommandSender sender, Known who) {
        Optional<Creator> creator = live.book().of(who.id());
        long now = live.clock().getAsLong();
        if (creator.isPresent() && rule.isLive(creator.get(), now, live.settings().get().manualAtMost())) {
            live.messages().send(sender, creator.get().detectedLive() ? "live.status.found" : "live.status.by-hand",
                    "name", who.name(), "lasted", StreamParser.spoken(lasted(creator.get(), now)),
                    "platform", creator.get().link() == null ? "" : creator.get().link().platform().spoken());
        } else {
            live.messages().send(sender, "live.status.off", "name", who.name());
        }
        ChannelLink link = creator.map(Creator::link).orElse(null);
        if (link == null) {
            live.messages().send(sender, "live.status.no-channel");
        } else {
            live.messages().send(sender, "live.status.channel", "channel", link.shown());
        }
    }

    private void list(Services live, CommandSender sender) {
        long now = live.clock().getAsLong();
        TwitchSettings settings = live.settings().get();
        List<Creator> streaming = !settings.creatorsEnabled() ? List.of() : live.book().all().values().stream()
                .filter(creator -> rule.isLive(creator, now, settings.manualAtMost()))
                .sorted(java.util.Comparator.comparing(Creator::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (streaming.isEmpty()) {
            live.messages().send(sender, "live.list.none");
            return;
        }
        live.messages().send(sender, "live.list.head", "count", String.valueOf(streaming.size()));
        for (Creator creator : streaming) {
            live.messages().sendPlain(sender, "live.list.line", "name", creator.name(),
                    "where", creator.link() == null ? "—" : creator.link().shown(),
                    "lasted", StreamParser.spoken(lasted(creator, now)));
        }
    }

    private static Duration lasted(Creator creator, long now) {
        long since = creator.detectedLive() && creator.liveSince() > 0 ? creator.liveSince() : creator.manualSince();
        return Duration.ofMillis(Math.max(0, now - since));
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            return ACTIONS.stream().filter(option -> option.startsWith(last)).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")
                && source.getSender().hasPermission(PermissionNodes.ADMIN)) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(last)).toList();
        }
        return List.of();
    }

    @Override
    public String describe() {
        return "live mode for creators: the pulsing dot by hand, or found from a linked Twitch or YouTube channel";
    }
}
