# Autodarts TV

A minimal Android TV app for [Autodarts](https://autodarts.io) — launches [play.autodarts.io](https://play.autodarts.io) fullscreen straight from your TV's home screen, with **D-pad focus navigation like a native TV app**.

No more opening a browser on your TV, typing URLs and fighting a clunky cursor with the remote. Install once, log in once, done.

## Download

Grab the latest `autodarts-tv-vX.Y.Z.apk` from the [Releases page](../../releases/latest) — no build setup needed. Then jump to [Installing on your TV](#installing-on-your-tv).

## Why

Autodarts runs great on a Raspberry Pi at the board, and the web app is the natural way to display matches on a TV. But TV browsers are painful: you have to navigate to the site every time, and the web app isn't built for remote controls. This app wraps the web app in a WebView and injects a custom spatial navigation layer so the D-pad behaves the way you'd expect on a TV.

## Features

- **Leanback launcher entry** — appears directly on the Android TV home screen, one click to start
- **Persistent login** — cookies and localStorage (Keycloak tokens) survive restarts; log in once and never again
- **Focus navigation** — the D-pad jumps between clickable elements, the selected one is highlighted with a white glow, OK clicks it
- **Smart directional search** — pressing *up* really goes up: same-column/row elements are preferred, with a 45° cone as fallback
- **Auto-scroll** — if there's nothing further in the pressed direction, the page scrolls and the search continues, so the very top and bottom of long pages stay reachable (works with virtualized lists too)
- **Cursor fallback** — press MENU to toggle a free-moving virtual mouse cursor for anything the focus navigation can't reach
- **Fullscreen, landscape, no browser chrome**

## Controls

| Button | Action |
|---|---|
| D-pad | Move the highlight to the nearest element in that direction |
| OK / Enter | Click the highlighted element (focuses text fields → on-screen keyboard) |
| BACK | Navigate back in the web app (doesn't close the app) |
| MENU | Toggle between focus navigation and free cursor mode |

In text fields: left/right move the caret, up/down leave the field and resume navigation.

## Building

Only needed if you want to build from source — otherwise just grab the APK from the [Releases page](../../releases/latest).

Requirements: [Android Studio](https://developer.android.com/studio) (ships with JDK 17 and the Android SDK). The Gradle wrapper is included.

```bash
git clone https://github.com/TheJim03/Autodarts-TV.git
cd Autodarts-TV
./gradlew assembleDebug        # Windows: .\gradlew.bat assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`. Alternatively open the project in Android Studio and use *Build → Build APK(s)*.

The debug APK is fine for personal use. For a signed release build, set up a keystore and run `./gradlew assembleRelease`.

## Installing on your TV

1. On the TV: *Settings → Device Preferences → About* → click *Build* 7 times to enable developer options
2. *Developer options* → enable *USB debugging* (and *Network debugging* / *ADB over network* if your TV has it as a separate switch)
3. From your computer (same network):

```bash
adb connect <tv-ip>:5555
adb install app/build/outputs/apk/debug/app-debug.apk   # later updates: adb install -r
```

Accept the debugging prompt on the TV the first time. If `adb connect` fails, reboot the TV after enabling developer options.

No ADB? Copy the APK over with an app like *Send Files to TV*, then install it with a file manager (allow installs from unknown sources).

## First start

You'll land on the Autodarts (Keycloak) login. Select the fields with the D-pad — OK opens the on-screen keyboard. Tip: the *Google TV Remote* app on your phone has a proper keyboard, which makes this one-time login much more pleasant. After that the session persists indefinitely.

## Customizing

- **Start URL**: `START_URL` in [`MainActivity.kt`](app/src/main/java/io/autodarts/tv/MainActivity.kt) — point it directly at your board or match view, e.g. `https://play.autodarts.io/boards/<board-id>/follow`
- **Highlight style**: the CSS block at the top of [`spatialnav.js`](app/src/main/assets/spatialnav.js)
- **Clickable element detection**: the `SELECTOR` list in `spatialnav.js`, in case some element isn't picked up
- **Cursor speed** (fallback mode): constants in [`CursorLayout.kt`](app/src/main/java/io/autodarts/tv/CursorLayout.kt)

Quick iteration tip: paste the contents of `spatialnav.js` into the DevTools console on play.autodarts.io in a desktop browser — the arrow keys behave exactly like the D-pad on the TV, no rebuild needed.

## How it works

The app is a single Activity hosting a WebView. After each page load it injects `spatialnav.js`, which:

1. collects all clickable elements (`button`, `a[href]`, `[role="button"]`, inputs, …)
2. on every arrow key press, runs a three-pass geometric search (same column/row → 45° cone → half-plane) to find the nearest element in that direction
3. highlights it, scrolls it into view, and clicks it on Enter via `el.click()` (which triggers React handlers)

A `MutationObserver` re-picks a highlight when the current element disappears (route changes, dialogs), and the script survives SPA navigation since it's injected once per document.

## Troubleshooting

- **Logged out after restart** — make sure you didn't clear the app's data; the session lives in the WebView's cookies/localStorage
- **An element can't be reached** — toggle cursor mode with MENU as a workaround, then please open an issue with a screenshot so the selector/search can be improved
- **Gradle sync fails** — usually a proxy/VPN blocking `services.gradle.org` or `dl.google.com`, or an outdated Android Studio (AGP 8.5 needs a recent version)

## Disclaimer

This is an unofficial community project and is not affiliated with or endorsed by autodarts.io. It simply displays the official web app.

## License

MIT