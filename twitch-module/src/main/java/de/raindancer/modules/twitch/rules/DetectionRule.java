package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.Observation;
import de.raindancer.modules.twitch.model.StreamInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * When a creator's linked channel counts as live and when as over: a new stream is told once; over only after
 * several offline looks in a row; a failed look changes nothing. One per watching server, started from what the
 * last watcher wrote down so a takeover tells nothing twice.
 */
public final class DetectionRule implements ITwitchRule {

    public sealed interface Change {
        record Live(StreamInfo stream) implements Change {
        }

        record Over() implements Change {
        }
    }

    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Integer> offlineLooks = new HashMap<>();

    /** @param known the stream each creator was last written down as live with */
    public DetectionRule(Map<UUID, String> known) {
        known.forEach((id, stream) -> {
            if (stream != null && !stream.isEmpty()) {
                current.put(id, stream);
            }
        });
    }

    public synchronized Optional<Change> observe(UUID creator, Observation seen, int endAfterLooks) {
        if (seen instanceof Observation.Live live) {
            offlineLooks.remove(creator);
            String id = live.stream().id();
            if (id.equals(current.get(creator))) {
                return Optional.empty();
            }
            current.put(creator, id);
            return Optional.of(new Change.Live(live.stream()));
        }
        if (seen == Observation.OFFLINE && current.containsKey(creator)) {
            int looks = offlineLooks.merge(creator, 1, Integer::sum);
            if (looks >= Math.max(1, endAfterLooks)) {
                current.remove(creator);
                offlineLooks.remove(creator);
                return Optional.of(new Change.Over());
            }
        }
        return Optional.empty();
    }

    /** Forgets everybody not among {@code linked} — a channel unlinked since. */
    public synchronized void retain(Collection<UUID> linked) {
        current.keySet().retainAll(linked);
        offlineLooks.keySet().retainAll(linked);
    }

    @Override
    public String describe() {
        return "when a creator's linked channel counts as live and when as over";
    }
}
