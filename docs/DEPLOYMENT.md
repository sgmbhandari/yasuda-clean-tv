# Deploying to another TV

Reviewed platform: CloudTV708, SK708D, Android 9/API 28, 1 GB RAM, 1920×1080 UI.
The branded Settings page may report a different Android version; the tool
reads the actual SDK property. A matching board name does not establish that
another firmware has compatible power/key routes. Optional system setup needs
firmware that permits maintenance `adb root`. No bootloader unlock or firmware
flash is used. The launcher runs as a normal app.

## Build

Install Python 3.9+, JDK 17, Android SDK platform 34/build-tools 34.0.0 and
platform-tools. Set `JAVA_HOME` and `ANDROID_HOME` if their default locations
do not apply. The wrapper downloads Gradle 8.7 and normal build dependencies.

```sh
git clone https://github.com/sgmbhandari/yasuda-clean-tv.git
cd yasuda-clean-tv
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
sh tools/build.sh
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

Build creates a private signing identity under
`~/.local/share/yasuda-clean-home/signing`, or `CLEAN_HOME_SIGNING_DIR` if set.
Back it up privately: updates must retain the same signing key. Neither the
key nor password belongs in Git. Outputs are `dist/Clean-Home-1.4.0.apk`, the
optional code-free power overlay and SHA256SUMS. The stated versions reproduce
the build process. First-time builds create a new key, so signatures and APK
hashes differ between users.

## Inspect and install alongside stock

Enable ADB in the TV's debug menu, leave it awake and connect from the same
local network. Substitute the actual address; the script has no default TV IP.

```sh
adb connect TV_IP:5555
python3 tools/deploy.py inspect --serial TV_IP:5555
python3 tools/deploy.py install --serial TV_IP:5555
python3 tools/deploy.py install --serial TV_IP:5555 --apply
```

Without `--apply`, mutation commands print a plan only. Inspect is read-only.
Install saves the original Home/packages/settings/input snapshot before
installing alongside stock. It does not change the default Home, system files
or packages. It cannot update a differently signed app: preserve the original
project/key.

Test D-pad navigation, long OK, Settings, each launcher input, Home/Back, volume,
mute, casting and real HDMI picture/audio before configuring system changes.
All Apps → long OK → Hide app removes it from the grid and favourite dock.
All Apps → Hidden apps → Show restores it. This does not disable the package,
block its remote hotkey or act as parental control. Hidden and missing-app
favourite bookmarks are retained.

## Optional system integration and TV-only reset

Setup saves that TV's original component states, Home, AppOps, exact hotkey
bytes and relevant settings. It refuses to adopt an existing modified power
overlay or recovery payload as factory state. Private data defaults to
`~/.local/share/yasuda-clean-home/devices`, outside the checkout. Keep it until
rollback is verified. Never copy one TV's recovery configuration to another.

```sh
python3 tools/deploy.py configure --serial TV_IP:5555 --power-home --smarttube-key
python3 tools/deploy.py configure --serial TV_IP:5555 --power-home --smarttube-key --apply
```

Setup prompts twice for a six-digit reset PIN without echoing it. There is no
default PIN. Omit `--smarttube-key` to preserve the existing key. If using that
option, first install and verify SmartTube stable from its official project.
Clean Home does not bundle third-party apps, credentials or paid services.

`--power-home` enables the startup alias, disables only the dedicated TIF
startup forwarder and adds `/vendor/overlay/CleanHomePower.apk`. The native TV
player/input/audio/casting/factory/debug services stay enabled.
`--smarttube-key` changes only the two reviewed YouTube aliases in
`/vendor/menu_config_xml/hotkey.xml`. Unknown layouts are rejected. All configure
variants select normal Home, enable the transient input-label permission and
provision reset before applying changes.

After setup, reboot manually. To save the additional firmware TV Home choice:

```sh
adb -s TV_IP:5555 shell am start -a android.intent.action.MAIN -c android.intent.category.TV_HOME --ez isLauncherGoToTv true
```

If Android asks, choose Clean Home → Always with the remote. Test real standby/
wake. The Source menu and power policy use the same TV_HOME action. The router
distinguishes known source-menu callers and source extras, forwarding those to
the real TV player in a separate task. Other firmware source-menu packages
need their route inspected before startup configuration.

Drop maintenance root when finished while keeping ADB open:

```sh
adb -s TV_IP:5555 unroot
```

Deployment never closes network ADB. Standby may suspend Wi-Fi; wake with the
physical remote. Advertising components are not automatically disabled on
other TVs: package names alone are not proof. Inspect manifests and behavior,
save original state and change only confirmed components with a rollback record.

## Restore

On the TV: photo icon → About Clean Home → Reset TV → chosen PIN → review scope
→ Restore and restart. Cancel is initially selected on the final confirmation.
Wrong PINs cannot request root or restore. No PIN is embedded in the source/APK.
Re-provision a forgotten PIN from a trusted computer using
`tools/provision_reset.py` and that TV's original script digest. Do not generate
a new baseline from already modified state.

From the original deployment computer:

```sh
python3 tools/deploy.py rollback --serial TV_IP:5555
python3 tools/deploy.py rollback --serial TV_IP:5555 --apply
```

Restore checks device identity and hashes, restores Home/component states/key
routes/input-label permission, moves its power overlay out of `/vendor`,
disables Clean Home and restarts. Other apps/data remain. It waits for Android's
delayed preference writes. Loaded overlay handles may defer read-only remount
until reboot; this is logged explicitly. Check
`/data/local/tmp/cleanhome-recovery/restore.log` for `RESTORE_APPLY_OK`, then
verify original Home/power/key/input behavior and that `/vendor` is read-only.
Do not uninstall Clean Home before restoring its startup changes.

Interrupted configuration retains the original snapshot and prepared reset
payload. Use rollback rather than configuring again against modified state.
Recovery restores recorded changes; it is not a factory firmware restore,
modification-history erasure or warranty eligibility guarantee.

## Verification

Build runs JVM tests and Android lint. The Python suite tests guarded recovery
generation, component states, precise key patching and reset verifiers. On a
configured test TV, install/run native tests with:

```sh
adb -s TV_IP:5555 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s TV_IP:5555 shell am instrument -w local.cleanhome.tv.test/local.cleanhome.tv.CleanHomeTestRunner
```

Native tests expect the configured routes, default apps and provisioned recovery.
They test reset preflight/confirmation, not a destructive reset. Demonstrate
rollback on each new TV. A fresh-TV configure/rollback cannot be fully tested
against an already configured owner TV without replacing its baseline, which
the script deliberately refuses. Physical Bluetooth audio, casting and HDMI
playback require real devices; session checks cannot certify them.
