package de.raindancer.modules.twitch.rules;

import de.raindancer.modules.twitch.model.ChannelLink;
import de.raindancer.modules.twitch.model.Platform;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** A pasted channel url read as a {@link ChannelLink} — Twitch or YouTube, a channel page only, never a video. */
public final class ChannelLinkRule implements ITwitchRule {

    private static final Pattern TWITCH_LOGIN = Pattern.compile("[a-z0-9_]{1,25}");
    private static final Pattern YOUTUBE_HANDLE = Pattern.compile("@[A-Za-z0-9._-]{3,30}");
    private static final Pattern YOUTUBE_CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern YOUTUBE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    /** Twitch pages that look like a channel and are not one. */
    private static final Set<String> TWITCH_PAGES = Set.of("directory", "videos", "settings", "p", "search",
            "downloads", "jobs", "turbo", "subscriptions", "inventory", "wallet", "drops", "login", "signup", "store");

    public Optional<ChannelLink> parse(String pasted) {
        if (pasted == null) {
            return Optional.empty();
        }
        String text = pasted.strip();
        if (text.startsWith("<") && text.endsWith(">")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        if (text.isEmpty() || text.length() > 200) {
            return Optional.empty();
        }
        if (!text.contains("://")) {
            text = "https://" + text;
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.") || host.startsWith("m.")) {
            host = host.substring(host.indexOf('.') + 1);
        }
        String[] path = uri.getPath() == null ? new String[0]
                : java.util.Arrays.stream(uri.getPath().split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
        return switch (host) {
            case "twitch.tv" -> twitch(path);
            case "youtube.com" -> youtube(path);
            default -> Optional.empty();
        };
    }

    private static Optional<ChannelLink> twitch(String[] path) {
        if (path.length == 0) {
            return Optional.empty();
        }
        String login = path[0].toLowerCase(Locale.ROOT);
        return TWITCH_LOGIN.matcher(login).matches() && !TWITCH_PAGES.contains(login)
                ? Optional.of(new ChannelLink(Platform.TWITCH, login)) : Optional.empty();
    }

    private static Optional<ChannelLink> youtube(String[] path) {
        if (path.length == 0) {
            return Optional.empty();
        }
        if (YOUTUBE_HANDLE.matcher(path[0]).matches()) {
            return Optional.of(new ChannelLink(Platform.YOUTUBE, path[0]));
        }
        if (path.length < 2) {
            return Optional.empty();
        }
        boolean fits = switch (path[0]) {
            case "channel" -> YOUTUBE_CHANNEL_ID.matcher(path[1]).matches();
            case "c", "user" -> YOUTUBE_NAME.matcher(path[1]).matches();
            default -> false;
        };
        return fits ? Optional.of(new ChannelLink(Platform.YOUTUBE, path[0] + "/" + path[1])) : Optional.empty();
    }

    @Override
    public String describe() {
        return "which pasted urls are a Twitch or YouTube channel, and what is kept of them";
    }
}
