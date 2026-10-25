package de.raindancer.modules.twitch.model;

/**
 * A creator's channel, already checked: a Twitch login (lower case), or a YouTube {@code @handle},
 * {@code channel/UC…}, {@code c/name} or {@code user/name}. Every url is built here from that, never taken as pasted.
 */
public record ChannelLink(Platform platform, String handle) {

    public String url() {
        return platform == Platform.TWITCH ? "https://www.twitch.tv/" + handle : "https://www.youtube.com/" + handle;
    }

    /** The page that shows the running stream: YouTube sends a channel's /live to its live video when there is one. */
    public String liveUrl() {
        return platform == Platform.TWITCH ? url() : url() + "/live";
    }

    public String shown() {
        return platform == Platform.TWITCH ? "twitch.tv/" + handle : "youtube.com/" + handle;
    }
}
