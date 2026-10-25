package de.raindancer.modules.twitch;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.twitch.command.LiveCommand;
import de.raindancer.modules.twitch.command.TwitchCommand;

import java.util.List;

/** What this module declares at bootstrap, and how it is filled in once the module runs. */
public final class TwitchCommands {

    private static volatile TwitchServices services;

    private TwitchCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(ModuleCommand.of("twitch", "The Twitch live announcements: status and tests",
                        new TwitchCommand(TwitchCommands::require))
                .taking("status | test live|ended [all] | reload | login <client-id> <client-secret> | logout"),
                ModuleCommand.of("live", "Live mode for creators: the pulsing dot, by hand or from a linked channel",
                        new LiveCommand(TwitchCommands::requireLive))
                        .taking("[on|off|status] [player] | link [player] <channel url> | unlink [player] | list"));
    }

    private static volatile LiveCommand.Services live;

    static void readyLive(LiveCommand.Services services) {
        live = services;
    }

    private static LiveCommand.Services requireLive() {
        LiveCommand.Services running = live;
        if (running == null) {
            throw new IllegalStateException("the twitch module is not running");
        }
        return running;
    }

    static void ready(TwitchServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
        live = null;
    }

    private static TwitchServices require() {
        TwitchServices live = services;
        if (live == null) {
            throw new IllegalStateException("the twitch module is not running");
        }
        return live;
    }
}
