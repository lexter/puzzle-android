# Slide Puzzle (Android)

Native Kotlin + Jetpack Compose rewrite of the Compose Multiplatform app in `../puzzle`. Same features and behavior: 4×4 to 10×10 grids, time attack and free play, a per-size top-10 leaderboard, and offline LAN races against Android or iPhone.

## Build

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Unit tests, including UI tests, run on the JVM with Robolectric. The app uses the same application ID and the same DataStore file as the old app, so an installed copy keeps its settings and scores.

## Layout (`app/src/main/java/com/lexterlabra/puzzle`)

- `game`: board model, shuffle, clock.
- `p2p`: wire framing, JSON messages, plain `java.net` sockets, NSD discovery (`_slidepuzzle._tcp`), and the match state machine.
- `data`: DataStore-backed settings and scores.
- `ui`: Compose screens and the Candy Pop glass theme.
