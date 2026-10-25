package de.raindancer.modules.twitch.model;

/** Where a creator streams. */
public enum Platform {
    TWITCH("Twitch"), YOUTUBE("YouTube");

    private final String spoken;

    Platform(String spoken) {
        this.spoken = spoken;
    }

    public String spoken() {
        return spoken;
    }
}
