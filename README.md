# Smart Overlay Pigs

Android Studio project that turns the Smart Browser Pigs Chrome extension into a **system overlay**.

Pigs bounce around on top of every app. A pink circle button opens the original popup (Start, snacks, modifiers, moods). They bounce off the **screen edges only** — not off text or UI.

## Open in Android Studio

1. Unzip this folder.
2. Open Android Studio → **File → Open** → select the unzipped `android-smart-pigs` directory.
3. Let Gradle sync. If Android Studio offers to generate a Gradle wrapper, accept it.
4. Plug in a phone (or start an emulator) with **API 26+**.
5. Click **Run**.

## First launch

1. Tap **Allow overlay permission** and enable **Display over other apps** for Smart Overlay Pigs.
2. Return to the app and tap **Start overlay**.
3. Press **Home**. Pigs stay on your wallpaper and other apps.
4. **Drag a pig** to fling it. Tap a pig to pet it.
5. Tap the **pink circle** to open the popup. Drag the circle to move it.
6. Stop from the app, or from the ongoing notification.

## What was ported

- Overlay pigs with walk / nap / dance / follow / eat / fling / ragdoll
- Circle bubble → popup HTML (Play, Pigs, Presets, Rig tabs)
- Modifiers: no gravity, bouncy, floaty, moon jump, magnet, super speed, giant, tiny, frozen, chaos, ghost, name tags, party, ragdoll, rope, blank face, wet pile
- Snacks, ball, toy, come here
- Custom pig image URL and size

Removed on purpose: climbing / colliding with page text. Android has no DOM, so pigs use the screen floor and edges only.

## Requirements

- Android Studio Ladybug / Meerkat or newer
- JDK 17
- compileSdk 35, minSdk 26
