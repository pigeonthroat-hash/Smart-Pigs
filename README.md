<p align="center">
  <img src="https://raw.githubusercontent.com/pigeonthroat-hash/Unofficial-Fan-Pig-Pet-Mobile-App/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="180" alt="SmartPigs">
</p>

<h1 align="center">SmartPigs</h1>

<p align="center">
  <strong>Virtual pigs that actually live on your screen.</strong>
</p>

<p align="center">
  Give your pigs personalities, let them move around with physics, interact with
  their environment, and watch them become their own little characters.
</p>

<p align="center">
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/releases">Releases</a>
  ·
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues">Issues</a>
  ·
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/discussions">Discussions</a>
</p>

<p align="center">
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/releases">
    <img src="https://img.shields.io/github/v/release/YOUR_GITHUB_USERNAME/smartpigs?style=flat-square&label=latest%20release" alt="Latest release">
  </a>
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/stargazers">
    <img src="https://img.shields.io/github/stars/YOUR_GITHUB_USERNAME/smartpigs?style=flat-square" alt="GitHub stars">
  </a>
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues">
    <img src="https://img.shields.io/github/issues/YOUR_GITHUB_USERNAME/smartpigs?style=flat-square" alt="GitHub issues">
  </a>
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs">
    <img src="https://img.shields.io/github/repo-size/YOUR_GITHUB_USERNAME/smartpigs?style=flat-square" alt="Repository size">
  </a>
  <a href="https://github.com/YOUR_GITHUB_USERNAME/smartpigs/commits/master">
    <img src="https://img.shields.io/github/last-commit/YOUR_GITHUB_USERNAME/smartpigs?style=flat-square" alt="Last commit">
  </a>
</p>

---

## 🐷 What is SmartPigs?

**SmartPigs** is an interactive virtual-pet project that puts small, autonomous pigs directly on your screen.

Instead of being simple animated decorations, SmartPigs is designed around the idea that every pig can feel like a little creature with its own behaviour.

Pigs can move around, be dragged by the user, react to interactions, use physics, think, and have individual personality traits.

SmartPigs currently has two major forms:

* 📱 **Android** — pigs that can appear over other applications using an Android overlay.
* 🌐 **Browser** — pigs that live directly inside web pages, with a browser-extension interface in development.

The project is intentionally experimental and playful. The goal is to keep adding systems that make the pigs feel increasingly alive.

---

## ✨ Features

### 🐷 Interactive pigs

Pigs are rendered as independent objects rather than one static image.

They can:

* Move around the screen
* Be dragged by the user
* Respond to interactions
* Exist simultaneously with other pigs
* Be added and removed dynamically
* Use different images
* Have configurable sizes

### 🧠 Personalities

SmartPigs includes the beginnings of a personality system.

A pig can have traits that influence how it behaves, allowing different pigs to act differently instead of simply repeating the exact same behaviour.

This system is intended to grow into a much more advanced behavioural model.

### 💭 Smart thinking

SmartPigs exposes a thinking system that can be triggered programmatically.

For example, the browser implementation includes:

```javascript
smartPigThink(0)
```

This allows a specific pig to perform a thought/behaviour cycle.

### ⚙️ Configurable settings

The Android implementation currently stores configuration such as:

* Pig count
* Pig width/size
* Pig image URL
* Behaviour modifiers
* Other persistent settings

### 🖱️ Physics and interaction

The project is built around making pigs behave like objects in a physical environment.

The long-term goal includes:

* Gravity
* Momentum
* Screen boundaries
* Collision behaviour
* Better hitboxes
* Pig-to-pig interaction
* More convincing movement

### 📱 Android overlay

The Android version can run SmartPigs as a foreground overlay service.

This allows the pigs to remain visible while another application is being used.

The overlay system uses Android's application-overlay window support and contains separate components for rendering, interaction, settings, and the SmartPigs engine.

### 🌐 Browser pets

The browser version brings the same concept into web pages.

The browser implementation can create and control pigs through JavaScript.

Example:

```javascript
smartPigInfo()
smartPigThink(0)
smartPigRemove()
```

The project also includes work toward a Chrome extension interface for managing the pigs.

---

# 📱 Android

The Android application is the primary native implementation of SmartPigs.

The application uses an overlay service to keep the pigs alive independently from the currently visible application.

### Android architecture

```text
SmartPigs
│
├── OverlayService
│   ├── OverlayCanvasView
│   ├── PigEngine
│   ├── Pig rendering
│   ├── Drag / pointer handling
│   ├── Bubble overlay
│   └── Popup overlay
│
├── SettingsStore
│   └── Persistent SmartPigs configuration
│
├── PigEngine
│   ├── Pig objects
│   ├── Movement
│   ├── Physics
│   ├── Behaviour
│   └── Screen bounds
│
└── WebView
    ├── SmartPigs UI
    └── JavaScript bridge
```

The Android overlay contains a canvas responsible for rendering the pigs and receiving pointer events.

A separate floating interface can provide access to SmartPigs controls and information.

---

## 🌐 Browser version

The browser version is designed to make SmartPigs behave like small web companions.

A page can create pigs dynamically and interact with them through JavaScript.

Example:

```javascript
smartPigThink(0);
```

Information about a pig can be queried using:

```javascript
smartPigInfo();
```

A pig can also be removed with:

```javascript
smartPigRemove();
```

The browser implementation is being developed alongside the Android version so that the underlying SmartPigs concepts can eventually be shared across both environments.

---

# 🧩 Chrome Extension

The Chrome extension is intended to provide a dedicated control panel for SmartPigs.

The planned interface includes:

* Current pig information
* Personality information
* Thinking controls
* Add-pig controls
* Remove-pig controls
* Image URL configuration
* Pig management

The extension is still under active development.

---

# 🎨 Custom Pigs

SmartPigs is designed to make changing the appearance of a pig easy.

A pig can use an image loaded from a URL, allowing different characters and creatures to be used without changing the core engine.

Example:

```javascript
https://example.com/my-pig.png
```

This makes it possible to create themed pigs, custom characters, community-made pets, and eventually more advanced character systems.

---

# 🛠️ Building SmartPigs

## Requirements

For Android development:

* Android Studio
* Android SDK
* JDK
* Gradle

The exact versions may change as the project develops.

## Clone the repository

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/smartpigs.git
cd smartpigs
```

Open the project in Android Studio and allow Gradle to synchronise.

## Build a debug APK

On Windows:

```powershell
gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew assembleDebug
```

The debug APK will normally be generated under:

```text
app/build/outputs/apk/debug/
```

## Build a release APK

```bash
./gradlew assembleRelease
```

The release output will normally be located under:

```text
app/build/outputs/apk/release/
```

For an actual public release, the application should be built and signed using the project's release signing configuration.

---

# 🚀 Installation

## Android

1. Download the latest APK from the [Releases](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/releases) page.
2. Install SmartPigs on your Android device.
3. Grant the required overlay permission.
4. Open SmartPigs.
5. Configure your pigs.
6. Start the overlay.

Once the overlay is running, the pigs can appear above other applications.

## Browser / Chrome

The browser extension can be loaded as an unpacked extension while it is under development.

1. Download or clone the repository.
2. Open:

```text
chrome://extensions
```

3. Enable **Developer mode**.
4. Choose **Load unpacked**.
5. Select the SmartPigs extension directory.

The exact extension structure may change during development.

---

# 🔧 Configuration

SmartPigs stores persistent settings so that configuration can survive between launches.

Current configuration concepts include:

| Setting     | Description                 |
| ----------- | --------------------------- |
| `pigCount`  | Number of pigs to maintain  |
| `pigWidth`  | Pig display width           |
| `imageUrl`  | Image used for the pig      |
| `modifiers` | Behaviour-related modifiers |

The Android implementation persists these settings through the SmartPigs settings store.

Example configuration concept:

```json
{
  "pigCount": 2,
  "pigWidth": 78,
  "imageUrl": "https://example.com/pig.png",
  "modifiers": {}
}
```

The exact structure may change as the project evolves.

---

# 🧠 How SmartPigs Works

At a high level, a SmartPig is an object managed by the SmartPigs engine.

```text
Input
  │
  ▼
┌───────────────────┐
│    PigEngine      │
├───────────────────┤
│ Position          │
│ Velocity          │
│ Physics           │
│ Personality       │
│ Behaviour         │
│ Appearance        │
└─────────┬─────────┘
          │
          ▼
     Render pig
          │
          ▼
   React to environment
```

This architecture allows the same general idea to be extended with more complicated behaviours without turning the pig into a simple collection of UI elements.

---

# 🧪 Experimental Systems

Some SmartPigs systems are still experimental.

These include:

* Advanced physics
* Personality behaviour
* Smart thinking
* Browser extension controls
* Large numbers of simultaneous pigs
* Collision and hitbox behaviour
* Pig-to-pig interaction
* More advanced autonomous behaviour

Experimental features may change or break between releases.

That is expected during development.

---

# 🗺️ Roadmap

The SmartPigs roadmap is intentionally open-ended.

### Core

* [x] Basic pig rendering
* [x] Multiple pigs
* [x] Pig dragging
* [x] Configurable pig count
* [x] Configurable pig size
* [x] Custom pig images
* [x] Persistent settings
* [x] Android overlay
* [x] Browser prototype

### Intelligence

* [x] Personality system foundation
* [x] Smart thinking interface
* [ ] Expanded personality traits
* [ ] Personality-driven movement
* [ ] Long-term behavioural memory
* [ ] Better autonomous decision making
* [ ] Pig-to-pig conversations
* [ ] Pig-to-pig relationships

### Physics

* [x] Basic movement
* [ ] Improved gravity
* [ ] Better momentum
* [ ] Improved collision detection
* [ ] Per-pig hitboxes
* [ ] Pig-to-pig collisions
* [ ] More realistic bouncing
* [ ] Environment interaction

### Android

* [x] Overlay service
* [x] Persistent configuration
* [x] Floating controls
* [ ] Improved settings UI
* [ ] Better overlay interactions
* [ ] Background optimisation
* [ ] Better performance with many pigs

### Browser

* [x] Browser prototype
* [x] JavaScript pig controls
* [x] Pig information API
* [x] Pig thinking API
* [x] Pig removal API
* [ ] Polished Chrome extension
* [ ] Extension settings UI
* [ ] Per-pig controls
* [ ] Better browser-page compatibility

### Polish

* [ ] Better animations
* [ ] Sound effects
* [ ] More pig designs
* [ ] More environments
* [ ] Better onboarding
* [ ] Accessibility improvements
* [ ] Performance optimisations

---

# 🐛 Known Issues

SmartPigs is actively being developed, so bugs and unfinished systems are expected.

Some current development areas include:

* Physics and collision behaviour can change rapidly.
* Large numbers of pigs may create significant performance or memory usage.
* Overlay interaction can have edge cases depending on the Android version.
* Browser-extension functionality is still being developed.
* Personality behaviour is not yet fully autonomous.
* Hitboxes and interaction boundaries are still being refined.

Found something that looks wrong?

Please open an issue and include as much useful information as possible.

---

# 📝 Reporting Bugs

Before opening an issue, check the existing issues to see whether the problem has already been reported.

When reporting a bug, include:

* SmartPigs version
* Device / browser
* Android version if applicable
* What you expected to happen
* What actually happened
* Steps to reproduce the problem
* Logs or screenshots when useful

Open an issue here:

👉 **[Report a bug](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues/new)**

---

# 💡 Feature Requests

SmartPigs is a project that is intentionally open to experimentation.

Have an idea for:

* A new personality
* A new pig behaviour
* A physics mechanic
* A new interaction
* A new UI
* A new platform
* A completely ridiculous pig feature

Open an issue and describe it.

👉 **[Request a feature](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues/new)**

---

# 🤝 Contributing

Contributions are welcome.

Before making a large change, opening an issue or discussion first is recommended so the direction can be coordinated.

For smaller improvements, bug fixes, documentation updates, and experiments, a pull request is welcome.

### Development principles

SmartPigs aims to stay:

* Lightweight
* Playful
* Extensible
* Easy to experiment with
* Friendly to contributors
* Focused on interesting behaviour

---

# 📂 Project Structure

The repository contains the Android application alongside the browser-related work.

A simplified structure looks like:

```text
smartpigs/
│
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       ├── res/
│   │       └── assets/
│   │
│   └── build.gradle
│
├── browser/
│   └── SmartPigs browser implementation
│
├── extension/
│   └── Chrome extension
│
├── docs/
│   └── Documentation and screenshots
│
├── README.md
├── LICENSE
└── .gitignore
```

The actual layout may evolve as the project grows.

---

# 🔐 Permissions & Privacy

The Android application may require overlay-related permissions to display pigs above other applications.

SmartPigs does **not** need to collect personal information simply to provide its core virtual-pet functionality.

Any future online features should document their data requirements clearly before being introduced.

For the most accurate information, review the permission declarations included with the version you install.

---

# 📸 Screenshots

Screenshots can be added here as the project reaches more polished releases.

Recommended layout:

```md
<p align="center">
  <img src="docs/screenshots/android-overlay.png" width="30%">
  <img src="docs/screenshots/pig-settings.png" width="30%">
  <img src="docs/screenshots/browser.png" width="30%">
</p>
```

A short animated GIF or video demonstration would also work especially well here.

---

# 📦 Releases

Stable releases are published on GitHub.

### Latest release

👉 **[View Releases](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/releases)**

Development builds may contain unfinished or experimental features.

For normal use, the latest stable release is recommended.

---

# 🔗 Links

| Resource            | Link                                                                                |
| ------------------- | ----------------------------------------------------------------------------------- |
| 📦 Releases         | [GitHub Releases](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/releases)       |
| 🐛 Bug reports      | [GitHub Issues](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues)           |
| 💡 Feature requests | [GitHub Issues](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/issues)           |
| 💬 Discussions      | [GitHub Discussions](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/discussions) |
| 📚 Documentation    | [Repository Wiki](https://github.com/YOUR_GITHUB_USERNAME/smartpigs/wiki)           |

---

# 🏷️ Versioning

SmartPigs releases use version numbers to identify published builds.

Development versions may contain breaking changes, experimental APIs, or incomplete features.

The `master` branch may therefore be less stable than the latest published release.

---

# 📜 License

SmartPigs is distributed under the license included in this repository.

See [`LICENSE`](LICENSE) for the complete license text.

---

# ❤️ About the Project

SmartPigs started with a simple idea:

> What if the little creatures on your screen actually behaved like little creatures?

The project is an ongoing experiment in virtual pets, physics, interfaces, and lightweight artificial behaviour.

The goal isn't just to make a pig move around.

The goal is to make you forget, for a moment, that it's just software.

---

<p align="center">
  <strong>🐷 SmartPigs</strong>
  <br>
  Virtual pets with personality.
</p>

<p align="center">
  Made with curiosity, code, and an unreasonable number of pigs.
</p>
