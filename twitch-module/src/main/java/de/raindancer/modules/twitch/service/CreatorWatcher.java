package de.raindancer.modules.twitch.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.twitch.TwitchSettings;
import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.model.Observation;
import de.raindancer.modules.twitch.model.Platform;
import de.raindancer.modules.twitch.rules.DetectionRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * On the one server that watches for creators: looks at every linked channel and writes down who was found live and
 * whose stream is over. Every server draws the dots from what is written down.
 */
public final class CreatorWatcher {

    private final CreatorBook book;
    private final Function<List<String>, Map<String, Observation>> twitch;
    private final Function<ChannelLink, Observation> youtube;
    private final Supplier<TwitchSettings> settings;
    private final LogChannel log;
    private final DetectionRule rule;

    /** @param twitch looks at Twitch logins in one go; null when there is no Twitch login — those channels go unwatched */
    public CreatorWatcher(CreatorBook book, Function<List<String>, Map<String, Observation>> twitch,
                          Function<ChannelLink, Observation> youtube, Supplier<TwitchSettings> settings, LogChannel log) {
        this.book = book;
        this.twitch = twitch;
        this.youtube = youtube;
        this.settings = settings;
        this.log = log;
        Map<UUID, String> known = new HashMap<>();
        book.reload().forEach((id, creator) -> known.put(id, creator.stream()));
        rule = new DetectionRule(known);
    }

    /** Off the server's threads. */
    public void poll() {
        TwitchSettings now = settings.get();
        if (!now.creatorsEnabled()) {
            return;
        }
        Map<UUID, Creator> creators = book.reload();
        Map<UUID, Observation> seen = new HashMap<>();
        Map<String, UUID> byLogin = new HashMap<>();
        for (Creator creator : creators.values()) {
            ChannelLink link = creator.link();
            if (link == null) {
                continue;
            }
            if (link.platform() == Platform.TWITCH) {
                byLogin.put(link.handle(), creator.id());
            } else {
                seen.put(creator.id(), youtube.apply(link));
            }
        }
        if (twitch == null) {
            // Nobody can ask Twitch any more: a stream written down as live would never be found over.
            byLogin.values().stream().filter(id -> !creators.get(id).stream().isEmpty())
                    .forEach(id -> book.change(id, null, Creator::over));
        } else if (!byLogin.isEmpty()) {
            twitch.apply(new ArrayList<>(byLogin.keySet()))
                    .forEach((login, observation) -> {
                        UUID id = byLogin.get(login);
                        if (id != null) {
                            seen.put(id, observation);
                        }
                    });
        }
        rule.retain(seen.keySet());
        seen.forEach((id, observation) -> rule.observe(id, observation, now.endAfterLooks()).ifPresent(change -> {
            if (change instanceof DetectionRule.Change.Live live) {
                log.info("{} is live on {}.", creators.get(id).name(), creators.get(id).link().platform().spoken());
                book.change(id, null, creator -> creator.detected(live.stream().id(), live.stream().title(),
                        live.stream().startedAt().toEpochMilli()));
            } else {
                book.change(id, null, Creator::over);
            }
        }));
    }
}
