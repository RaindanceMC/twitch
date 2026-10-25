package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.Creator;
import net.kyori.adventure.text.format.TextColor;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Who shows as live, who has just gone live, and what shade the pulsing dot is at a moment. */
public final class LiveModeRule implements ITwitchRule {

    /** Bright to dark and back; the tablist and the nametags redraw about twice a second, so one shade each. */
    public static final List<TextColor> PULSE = List.of(TextColor.color(0xFF3030), TextColor.color(0xD01E1E),
            TextColor.color(0x8A1010), TextColor.color(0xB81818));
    public static final long PULSE_STEP_MS = 500;

    public boolean isLive(Creator creator, long now, Duration manualAtMost) {
        if (creator == null) {
            return false;
        }
        boolean byHand = creator.manualSince() > 0 && now - creator.manualSince() < manualAtMost.toMillis();
        return byHand || creator.detectedLive();
    }

    public Set<UUID> liveNow(Map<UUID, Creator> creators, long now, Duration manualAtMost) {
        Set<UUID> live = new HashSet<>();
        creators.forEach((id, creator) -> {
            if (isLive(creator, now, manualAtMost)) {
                live.add(id);
            }
        });
        return live;
    }

    public Set<UUID> wentLive(Set<UUID> before, Set<UUID> after) {
        Set<UUID> fresh = new HashSet<>(after);
        fresh.removeAll(before);
        return fresh;
    }

    /** Taken from the clock alone, so every server and every place a name is drawn pulses together. */
    public static TextColor pulseShade(long now) {
        return PULSE.get((int) Math.floorMod(now / PULSE_STEP_MS, (long) PULSE.size()));
    }

    @Override
    public String describe() {
        return "who shows as live, and the shade of the pulsing dot";
    }
}
