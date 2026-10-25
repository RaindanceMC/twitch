package de.raindancer.modules.twitch.rules;

import de.raindancer.core.world.time.Times;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;

/** What the Twitch screens say, as MiniMessage text. */
public final class ScreenLineRule implements ITwitchRule {

    private static final String HEAD = "<gold><bold>Live on Twitch</bold></gold>";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** @param liveFor null when the start is not known
     *  @param viewers 0 when Twitch's count is not known on this server — then it is left out */
    public String live(String name, String title, String game, Duration liveFor, int viewers) {
        StringBuilder text = new StringBuilder(HEAD).append("\n<red>● <white>").append(MINI.escapeTags(name));
        if (!title.isBlank()) {
            text.append("\n<gray>").append(MINI.escapeTags(title));
        }
        if (!game.isBlank()) {
            text.append("\n<gray>playing <white>").append(MINI.escapeTags(game));
        }
        if (liveFor != null) {
            text.append("\n<gray>live for <white>").append(Times.brief(liveFor));
        }
        if (viewers > 0) {
            text.append("\n<gray>watching <white>").append(viewers);
        }
        return text.toString();
    }

    public String offline(String channel) {
        return HEAD + "\n<gray>Offline right now\n<dark_gray>twitch.tv/" + channel;
    }

    /** A viewer's own standing for the advancements that depend on being there. */
    public String you(boolean live, boolean fromStart, int minutesNear, int needed) {
        String head = "<gold><bold>Your stream</bold></gold>";
        if (!live) {
            return head + "\n<gray>Nobody is live right now";
        }
        String near = minutesNear >= needed ? "<green>✔ " + needed + "/" + needed
                : "<white>" + minutesNear + "</white><gray>/" + needed;
        return head + "\n" + (fromStart ? "<green>✔ Tuned in from the start" : "<gray>Joined after the start")
                + "\n<gray>near the streamer " + near;
    }

    @Override
    public String describe() {
        return "what the Twitch screens say: who is live and how long, and a viewer's own standing";
    }
}
