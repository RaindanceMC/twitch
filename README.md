# RaindanceMC/twitch

- **`twitch-module`** — Rain's Flexible Modules — Twitch: A chat announcement on every server of the network when a Twitch channel goes live, and another when the stream ends. One server asks Twitch; the others are told over the network link.
- **`twitch-standalone`** — Rain's Twitch: Rain's Twitch, as a plugin of its own. Nothing here but a build file and a descriptor: the twitch module and the standard wrapper are shaded together, and that is the whole plugin.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `twitch-standalone/target/RainsTwitch-0.7.0.jar` — also published as `de.raindancer.modules:twitch-standalone:0.7.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
