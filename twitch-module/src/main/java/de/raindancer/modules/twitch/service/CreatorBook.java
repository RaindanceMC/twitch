package de.raindancer.modules.twitch.service;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Creator;
import de.raindancer.modules.twitch.rules.ChannelLinkRule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Every creator of the network — channel, live by hand, live as found — in one network document, so each server
 * draws the same dots. Kept in memory as a picture that is replaced whole on every read: name badges ask it on every
 * chat line and tablist refresh, so asking must be cheap and safe from any thread.
 */
public final class CreatorBook {

    private static final String ROOT = "creators";

    private final YamlStore store;
    private final LogChannel log;
    private final ChannelLinkRule links = new ChannelLinkRule();
    private final List<BiConsumer<Map<UUID, Creator>, Map<UUID, Creator>>> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<UUID, Creator> picture = Map.of();

    public CreatorBook(YamlStore store, LogChannel log) {
        this.store = store;
        this.log = log;
    }

    public Map<UUID, Creator> all() {
        return picture;
    }

    public Optional<Creator> of(UUID id) {
        return Optional.ofNullable(picture.get(id));
    }

    /** Told the picture before and after every read. */
    public void onChange(BiConsumer<Map<UUID, Creator>, Map<UUID, Creator>> listener) {
        listeners.add(listener);
    }

    /** Reads the document again; a document that could not be read keeps the picture as it was. Blocking. */
    public synchronized Map<UUID, Creator> reload() {
        Optional<YamlConfiguration> read = store.readIfReadable();
        if (read.isEmpty()) {
            log.warn("The creators could not be read; the live dots stay as they were.");
            return picture;
        }
        Map<UUID, Creator> before = picture;
        Map<UUID, Creator> after = Map.copyOf(readAll(read.get()));
        picture = after;
        for (var listener : listeners) {
            try {
                listener.accept(before, after);
            } catch (RuntimeException failed) {
                log.warn("A listener to the creators failed: {}", failed.getMessage());
            }
        }
        return after;
    }

    /**
     * Changes one creator in the document and reads it again. A creator left with nothing is removed entirely.
     *
     * @param name their name now, or null to keep the one written down
     * @return whether it was written
     */
    public boolean change(UUID id, String name, UnaryOperator<Creator> change) {
        boolean written = store.update(yaml -> {
            String path = ROOT + "." + id;
            Creator now = yaml.isConfigurationSection(path) ? read(id, yaml.getConfigurationSection(path))
                    : Creator.of(id, name);
            Creator changed = change.apply(now.named(name));
            if (changed.empty()) {
                yaml.set(path, null);
            } else {
                write(yaml, path, changed);
            }
        });
        if (!written) {
            log.warn("The creators could not be written; the change to {} was not kept.", id);
        }
        reload();
        return written;
    }

    private Map<UUID, Creator> readAll(YamlConfiguration yaml) {
        Map<UUID, Creator> found = new HashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            return found;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                found.put(id, read(id, root.getConfigurationSection(key)));
            } catch (IllegalArgumentException | NullPointerException odd) {
                log.warn("A creator entry '{}' could not be read and is left out.", key);
            }
        }
        return found;
    }

    private Creator read(UUID id, ConfigurationSection section) {
        ChannelLink link = links.parse(section.getString("channel", "")).orElse(null);
        return new Creator(id, section.getString("name", ""), link, section.getLong("manual-since", 0),
                section.getString("stream", ""), section.getString("title", ""), section.getLong("live-since", 0),
                section.getString("hidden-stream", ""));
    }

    private static void write(YamlConfiguration yaml, String path, Creator creator) {
        yaml.set(path, null);
        yaml.set(path + ".name", creator.name());
        if (creator.link() != null) {
            yaml.set(path + ".channel", creator.link().url());
        }
        if (creator.manualSince() > 0) {
            yaml.set(path + ".manual-since", creator.manualSince());
        }
        if (!creator.stream().isEmpty()) {
            yaml.set(path + ".stream", creator.stream());
            yaml.set(path + ".title", creator.title());
            yaml.set(path + ".live-since", creator.liveSince());
        }
        if (!creator.hiddenStream().isEmpty()) {
            yaml.set(path + ".hidden-stream", creator.hiddenStream());
        }
    }
}
