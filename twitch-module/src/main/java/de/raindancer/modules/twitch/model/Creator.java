package de.raindancer.modules.twitch.model;

import java.util.UUID;

/**
 * Somebody who may show as live: by hand ({@code manualSince}, epoch millis, 0 for off), or because their linked
 * channel was found live ({@code stream}, empty when not). {@code hiddenStream} is a found stream they switched off —
 * hidden until the channel starts another one.
 */
public record Creator(UUID id, String name, ChannelLink link, long manualSince, String stream, String title,
                      long liveSince, String hiddenStream) {

    public Creator {
        name = name == null ? "" : name;
        stream = stream == null ? "" : stream;
        title = title == null ? "" : title;
        hiddenStream = hiddenStream == null ? "" : hiddenStream;
    }

    public static Creator of(UUID id, String name) {
        return new Creator(id, name, null, 0, "", "", 0, "");
    }

    public Creator named(String newName) {
        return newName == null || newName.isBlank() ? this
                : new Creator(id, newName, link, manualSince, stream, title, liveSince, hiddenStream);
    }

    public Creator on(long now) {
        return new Creator(id, name, link, now, stream, title, liveSince, "");
    }

    /** Off by hand, and the stream found right now hidden with it. */
    public Creator off() {
        return new Creator(id, name, link, 0, stream, title, liveSince, stream.isEmpty() ? hiddenStream : stream);
    }

    public Creator linked(ChannelLink newLink) {
        return new Creator(id, name, newLink, manualSince, "", "", 0, "");
    }

    public Creator unlinked() {
        return new Creator(id, name, null, manualSince, "", "", 0, "");
    }

    public Creator detected(String newStream, String newTitle, long since) {
        return new Creator(id, name, link, manualSince, newStream, newTitle, since, hiddenStream);
    }

    public Creator over() {
        return new Creator(id, name, link, manualSince, "", "", 0, "");
    }

    /** Whether the linked channel is live and they have not hidden this stream. */
    public boolean detectedLive() {
        return !stream.isEmpty() && !stream.equals(hiddenStream);
    }

    /** Nothing left worth keeping. */
    public boolean empty() {
        return link == null && manualSince == 0 && stream.isEmpty();
    }
}
