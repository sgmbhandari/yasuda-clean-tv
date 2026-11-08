# Yasuda Clean Home

A lightweight native Android TV launcher for **CloudTV708 / SK708D, Android 9
(API 28)**. Kotlin, native Views, 1 GB RAM, 1920×1080 interface. Clean Home 1.4
has glass panels, a large 12-hour clock/day/date, smooth remote focus, a favourite
dock and small centered text navigation. No accounts, advertising, analytics or
listening service. This is an independent project, not a Yasuda/CVTE or Apple
product.

## Features

- **App hiding:** All Apps → hold OK → Hide app. Hidden apps disappear from the
  grid and dock. All Apps → Hidden apps restores them. Packages remain enabled
  and installed; favourite bookmarks are retained. This is presentation control,
  not parental control or app access blocking.
- **Favourites:** up to six cards; hold OK on the dock to reorder/remove. In All
  Apps, favourites have a star in the upper-right. Hold OK for favourite/hide
  actions. Home recalls the last app card; Back closes panels.
- **Wallpapers:** 24 bundled licensed landscape photos; rotate every 30 minutes
  while Home is visible. A fresh batch of 12 Wikimedia Commons landscapes is
  downloaded every 48 hours while visible and saved locally. No key/backend or
  wake-up alarm. Failed refreshes keep the previous collection; cache is capped
  at 50 MiB. The photo icon provides Next, Pause/Resume, image credits and About.
- **Reset:** photo icon → About Clean Home → Reset TV. Each deployment chooses
  its own six-digit PIN. Correct PIN opens a separate final confirmation;
  Cancel is initially selected. Saved original settings are restored, Clean
  Home disabled and the TV restarted. Apps/data remain. No universal PIN,
  signing key or private recovery configuration is shipped in the APK/source.
- **TV integration:** optional guarded power-on Home and SmartTube key setup;
  five-second input labels. The TV_HOME router keeps native Source selection
  separate from power-on Home routing. Input/audio/casting/factory/debug services
  remain available. No key-watching accessibility or persistent root service.

## Build and deploy

Requirements: Python 3.9+, JDK 17, Android SDK platform 34/build-tools 34.0.0 and
platform-tools. Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24 are pinned. Set
`JAVA_HOME` and `ANDROID_HOME` when needed.

```sh
sh tools/build.sh
python3 -m unittest discover -s tools -p 'test_*.py' -v
adb connect TV_IP:5555
python3 tools/deploy.py inspect --serial TV_IP:5555
python3 tools/deploy.py install --serial TV_IP:5555 --apply
```

The signed APK and hashes are in `dist/`. Signing keys stay privately outside
source control. Install alongside stock first and test with the physical remote.
Mutation commands print a plan unless `--apply` is present. Optional system
integration requires this firmware's maintenance ADB root and a per-TV backup.
Deployment leaves network ADB open. See the complete [deployment, verification
and rollback instructions](docs/DEPLOYMENT.md) before configuring another TV.

## Scope and limits

Bluetooth is **not working on the tested firmware**: no USB controller was
enumerated, and a temporary manager probe hit a kernel timer/stack failure.
The original configuration was restored. Driver files alone do not establish
working hardware. See [Bluetooth evidence and limits](docs/BLUETOOTH.md).

The public deployment script does not blindly disable vendor packages. Package
names are leads, not proof of advertising behavior. Clean Home itself has no ads;
third-party streaming apps have their own policies. No vendor firmware APKs or
third-party app binaries are redistributed. Optional SmartTube integration
requires a separately installed, verified official stable build.

Reset restores recorded changes. It does not replace factory firmware, erase
modification history or guarantee warranty eligibility. The PIN protects the
launcher UI, not a root-capable ADB endpoint. See [security notes](SECURITY.md).

The project has been tested on one configured owner TV. The guarded Python
installer's inspect/plan paths and generated rollback scripts are tested;
complete fresh-TV configuration still needs verification on a second unit.
Physical HDMI/casting/audio playback cannot be certified by input-session tests.

## Credits and license

Code: [MIT](LICENSE). Photos: their authors' individual free licenses, with
[source pages and attribution](docs/WALLPAPERS.md) and on-TV About this image.
Photos are resized and center-cropped for display. [Wallpaper refresh details](docs/WALLPAPER-REFRESH.md).
Apple-inspired visual treatment uses no redistributed Apple fonts or artwork.
