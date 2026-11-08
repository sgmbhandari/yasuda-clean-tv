#!/usr/bin/env python3
"""Guarded SK708D deployment. Inspection is read-only; mutations require --apply."""
import argparse
import datetime
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from provision_reset import provision, verifier

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "local.cleanhome.tv"
HOME = PACKAGE + "/.HomeActivity"
ALIAS = PACKAGE + "/.StartupHome"
BOOT_ALIAS = PACKAGE + "/.BootHome"
FORWARDER = "com.cvte.tiflauncher/.MainActivity"
HOTKEY = "/vendor/menu_config_xml/hotkey.xml"
OVERLAY = "/vendor/overlay/CleanHomePower.apk"
REMOTE = "/data/local/tmp/cleanhome-recovery"


def sha(file): return hashlib.sha256(Path(file).read_bytes()).hexdigest()


def component_state(xml, component):
    package, name = component.split("/")
    if name.startswith("."): name = package + name
    entry = ET.fromstring(xml).find(f"pkg[@name='{package}']")
    if entry is None: raise ValueError("Missing expected package: " + package)
    for tag, value in [("enabled-components", 1), ("disabled-components", 2)]:
        if any(x.get("name") == name for x in entry.findall(tag + "/item")): return value
    return 0


def patch_hotkey(text, activity):
    if not re.fullmatch(r"[A-Za-z0-9_.$]+", activity): raise ValueError("Invalid SmartTube component")
    count = 0
    def patch(match):
        nonlocal count
        block = match.group(0)
        if not any(key in block for key in ["KEYCODE_CVT_YOUTUBE", "KEYCODE_QUICK_START5"]): return block
        if 'package="com.google.android.youtube.tv"' not in block: raise ValueError("Unrecognized YouTube key route; inspect this firmware first")
        count += 1
        block = block.replace('package="com.google.android.youtube.tv"', 'package="org.smarttube.stable"')
        return re.sub(r'class="[^"]*"', lambda _: 'class="' + activity + '"', block, count=1)
    result = re.sub(r"<HotKey\b[^>]*>.*?</HotKey>", patch, text, flags=re.S)
    if count != 2: raise ValueError("Expected exactly two dedicated YouTube key aliases")
    ET.fromstring(result)
    return result


class Device:
    def __init__(self, adb, serial): self.adb, self.serial = adb, serial
    def call(self, *args, timeout=45):
        result = subprocess.run([self.adb, "-s", self.serial, *args], capture_output=True, text=True, timeout=timeout)
        if result.returncode: raise RuntimeError(result.stderr.strip() or result.stdout.strip())
        return result.stdout.strip()
    def shell(self, *args): return self.call("shell", shlex.join(args))
    def verified(self):
        values = [self.shell("getprop", key) for key in ["ro.product.model", "ro.product.board", "ro.build.version.sdk"]]
        if values != ["CloudTV708", "SK708D", "28"]: raise RuntimeError("Not the reviewed CloudTV708/SK708D/API28 platform")
        return values
    def identity(self):
        values = [self.shell("getprop", "ro.serialno"), self.shell("settings", "get", "secure", "android_id")]
        if not values[0] or values[1] in ["", "null"]: raise RuntimeError("No stable device identity; refusing cross-TV recovery risk")
        return hashlib.sha256("\n".join(values).encode()).hexdigest()
    def root(self):
        if self.shell("id", "-u") == "0": return
        self.call("root")
        if ":" in self.serial:
            subprocess.run([self.adb, "disconnect", self.serial], check=True, capture_output=True, timeout=5)
        for _ in range(20):
            time.sleep(.5)
            if ":" in self.serial:
                subprocess.run([self.adb, "connect", self.serial], capture_output=True, timeout=5)
            try:
                if self.shell("id", "-u") == "0": return
            except (RuntimeError, subprocess.TimeoutExpired): pass
        raise RuntimeError("Firmware does not permit maintenance ADB root; install alongside stock only")
    def push(self, file, remote): self.call("push", str(file), remote)
    def pull(self, remote, file): self.call("pull", remote, str(file))


def save(file, data):
    file.write_text(json.dumps(data, indent=2) + "\n"); file.chmod(0o600)


def release(file):
    file = file.resolve()
    sums = file.parent / "SHA256SUMS"
    if not sums.is_file() or not any(line.split()[0] == sha(file) and Path(line.split()[-1]).name == file.name for line in sums.read_text().splitlines() if len(line.split()) == 2):
        raise RuntimeError("APK must match its adjacent SHA256SUMS; build locally or use a verified release")


def backup(device, folder):
    folder.mkdir(mode=0o700, parents=True, exist_ok=False)
    identity = device.identity()
    home = device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").splitlines()[-1]
    if not re.fullmatch(r"[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+", home): raise RuntimeError("Choose a default Home before setup")
    snapshot = {"identity": identity, "home": home, "fingerprint": device.shell("getprop", "ro.build.fingerprint"), "phase": "snapshot", "steps": []}
    for name, args in {"packages": ("dumpsys", "package"), "global": ("settings", "list", "global"), "secure": ("settings", "list", "secure"), "system": ("settings", "list", "system"), "inputs": ("dumpsys", "tv_input")}.items():
        file = folder / (name + ".txt"); file.write_text(device.shell(*args)); file.chmod(0o600)
    save(folder / "snapshot.json", snapshot)
    return snapshot


def recovery_script(data):
    """All interpolated inputs are validated identifiers, digests or shell-quoted values."""
    q = shlex.quote
    for key in ["hotkey_original", "hotkey_patched", "overlay_sha"]:
        if data.get(key) and not re.fullmatch(r"[a-f0-9]{64}", data[key]): raise ValueError("Bad backup digest")
    ops = {0: "default-state", 1: "enable", 2: "disable"}
    commands = []
    checks = []
    if data.get("power_home"):
        checks.append(f'if [ -e {OVERLAY} ]; then check {OVERLAY} {data["overlay_sha"]}; fi')
        commands += [f'if [ -e {OVERLAY} ]; then mv {OVERLAY} "$BASE/CleanHomePower-retired.apk"; fi',
                     f'pm {ops[data["components"][FORWARDER]]} --user 0 {FORWARDER}',
                     f'pm {ops[data["components"][ALIAS]]} --user 0 {ALIAS}',
                     f'pm {ops[data["components"][BOOT_ALIAS]]} --user 0 {BOOT_ALIAS}']
    if data.get("smarttube_key"):
        checks += [f'check "$BASE/hotkey.xml" {data["hotkey_original"]}',
                   f'actual="$(sha256sum {HOTKEY})"; actual="${{actual%% *}}"; [ "$actual" = {data["hotkey_original"]} ] || [ "$actual" = {data["hotkey_patched"]} ]']
        commands += [f'cp "$BASE/hotkey.xml" {HOTKEY}', f'chmod 0644 {HOTKEY}', f'chown 0:0 {HOTKEY}', f'restorecon {HOTKEY}', f'check {HOTKEY} {data["hotkey_original"]}']
    check_text = "\n".join(checks)
    command_text = "\n".join(commands)
    vendor_open = "mount -o remount,rw /vendor" if commands else ""
    vendor_close = "if ! mount -o remount,ro /vendor; then echo VENDOR_READ_ONLY_REQUIRES_REBOOT; fi" if commands else ""
    return f'''#!/system/bin/sh
set -eu
BASE={REMOTE}
[ "$(id -u)" = 0 ]
[ "$(getprop ro.product.model)" = CloudTV708 ]
[ "$(getprop ro.product.board)" = SK708D ]
[ "$(getprop ro.build.version.sdk)" = 28 ]
[ "$(getprop ro.serialno)" = {q(data['serialno'])} ]
[ "$(settings get secure android_id)" = {q(data['android_id'])} ]
check() {{ actual="$(sha256sum "$1")"; [ "${{actual%% *}}" = "$2" ]; }}
{check_text}
[ "$(pm path {q(data['home'].split('/')[0])})" != "" ]
if [ "${{1:-}}" = --check ]; then echo RESTORE_CHECK_OK; exit 0; fi
[ "${{1:-}}" = --apply ]
restart() {{ result=$?; trap - EXIT; [ "$result" = 0 ] || echo RESTORE_FAILED_CHECK_LOG; sync; sleep 2; reboot; }}
trap restart EXIT
{vendor_open}
{command_text}
{vendor_close}
appops set {PACKAGE} SYSTEM_ALERT_WINDOW {q(data['overlay_mode'])}
appops write-settings
cmd package set-home-activity --user 0 {q(data['home'])}
resolved="$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME)"
case "$resolved" in *{q(data['home'].split('/')[0] + '/')}*) ;; *) echo ORIGINAL_HOME_NOT_SELECTED; exit 1 ;; esac
pm disable-user --user 0 {PACKAGE}
sleep 12
grep 'name="{PACKAGE}"' /data/system/users/0/package-restrictions.xml | grep -q 'enabled="3"'
echo RESTORE_APPLY_OK
'''


def configure(device, folder, args):
    device.root(); device.verified()
    if folder.exists(): raise RuntimeError("A recovery baseline already exists for this TV. Use rollback; do not replace its original snapshot.")
    if device.shell("sh", "-c", f"test -e {OVERLAY} && echo present || echo absent") == "present":
        raise RuntimeError("An existing power overlay needs its original project journal; refusing to adopt it as factory state")
    if device.shell("sh", "-c", f"test -e {REMOTE}/restore.sh && echo present || echo absent") == "present":
        raise RuntimeError("A recovery payload already exists; preserve it and use its original project")
    pin = getpass.getpass("Choose reset PIN (six digits): ")
    if pin != getpass.getpass("Confirm reset PIN: "): raise ValueError("PINs do not match")
    access = verifier(pin); pin = None
    # Flush delayed PackageManager writes before reading component state.
    time.sleep(12)
    data = backup(device, folder)
    if data["home"].startswith(PACKAGE + "/"): raise RuntimeError("Original Home is already Clean Home. Restore its original baseline before configuring another deployment.")
    data.update(power_home=args.power_home, smarttube_key=args.smarttube_key,
                serialno=device.shell("getprop", "ro.serialno"), android_id=device.shell("settings", "get", "secure", "android_id"))
    restrictions = device.shell("cat", "/data/system/users/0/package-restrictions.xml")
    (folder / "package-restrictions.xml").write_text(restrictions); (folder / "package-restrictions.xml").chmod(0o600)
    data["components"] = {c: component_state(restrictions, c) for c in [ALIAS, BOOT_ALIAS, FORWARDER]}
    mode = re.search(r"SYSTEM_ALERT_WINDOW:\s*(\w+)", device.shell("appops", "get", PACKAGE, "SYSTEM_ALERT_WINDOW"))
    data["overlay_mode"] = mode.group(1) if mode else "default"
    if data["overlay_mode"] not in ["default", "allow", "ignore", "deny", "foreground"]: raise RuntimeError("Unrecognized original overlay permission")
    if args.power_home:
        overlay = ROOT / "dist/Clean-Home-Power-1.0.0.apk"; release(overlay); data["overlay_sha"] = sha(overlay)
    if args.smarttube_key:
        route = device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LEANBACK_LAUNCHER", "-p", "org.smarttube.stable").splitlines()[-1]
        if not route.startswith("org.smarttube.stable/"): raise RuntimeError("Install an official SmartTube stable build before remapping its key")
        metadata = device.shell("stat", "-c", "%a:%u:%g", HOTKEY)
        if metadata != "644:0:0": raise RuntimeError("Unreviewed hotkey file ownership/mode")
        device.pull(HOTKEY, folder / "hotkey.xml"); (folder / "hotkey.xml").chmod(0o600)
        (folder / "hotkey-patched.xml").write_text(patch_hotkey((folder / "hotkey.xml").read_text(), route.split("/", 1)[1]))
        (folder / "hotkey-patched.xml").chmod(0o600)
        data.update(hotkey_original=sha(folder / "hotkey.xml"), hotkey_patched=sha(folder / "hotkey-patched.xml"))
    script = folder / "restore.sh"; script.write_text(recovery_script(data)); script.chmod(0o600)
    subprocess.run(["sh", "-n", str(script)], check=True)
    access["scriptSha256"] = sha(script)
    access["scope"] = "Restores the saved original Home, input-label permission" + (", startup/power configuration" if args.power_home else "") + (" and YouTube remote-key routes" if args.smarttube_key else "") + ". Other apps and personal data are preserved."
    data["script_sha"] = sha(script); data["phase"] = "prepared"; save(folder / "snapshot.json", data)
    device.shell("mkdir", "-p", REMOTE)
    for name in ["restore.sh"] + (["hotkey.xml"] if args.smarttube_key else []):
        device.push(folder / name, REMOTE + "/" + name); device.shell("chmod", "0600", REMOTE + "/" + name)
    device.shell("chown", "-R", "0:0", REMOTE); device.shell("chmod", "0700", REMOTE)
    if "RESTORE_CHECK_OK" not in device.shell("sh", REMOTE + "/restore.sh", "--check"): raise RuntimeError("Restore preflight failed")
    provision(device.adb, device.serial, access)
    def step(name): data["steps"].append(name); save(folder / "snapshot.json", data)
    try:
        step("select-home"); device.shell("cmd", "package", "set-home-activity", "--user", "0", HOME)
        if PACKAGE + "/" not in device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"):
            raise RuntimeError("Clean Home did not become normal Home")
        step("input-banner-permission"); device.shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow"); device.shell("appops", "write-settings")
        if args.power_home or args.smarttube_key:
            device.shell("mount", "-o", "remount,rw", "/vendor")
            try:
                if args.power_home:
                    step("power-overlay"); device.push(overlay, OVERLAY); device.shell("chmod", "0644", OVERLAY); device.shell("chown", "0:0", OVERLAY); device.shell("restorecon", OVERLAY)
                    if device.shell("sha256sum", OVERLAY).split()[0] != data["overlay_sha"]: raise RuntimeError("Overlay write did not verify")
                if args.smarttube_key:
                    step("youtube-key"); device.push(folder / "hotkey-patched.xml", "/data/local/tmp/cleanhome-hotkey.xml")
                    device.shell("cp", "/data/local/tmp/cleanhome-hotkey.xml", HOTKEY); device.shell("chmod", "0644", HOTKEY); device.shell("chown", "0:0", HOTKEY); device.shell("restorecon", HOTKEY)
                    if device.shell("sha256sum", HOTKEY).split()[0] != data["hotkey_patched"]: raise RuntimeError("Hotkey write did not verify")
            finally: device.shell("mount", "-o", "remount,ro", "/vendor")
        if args.power_home:
            step("startup-routing"); device.shell("pm", "enable", "--user", "0", ALIAS); device.shell("pm", "enable", "--user", "0", BOOT_ALIAS); device.shell("pm", "disable", "--user", "0", FORWARDER)
            if BOOT_ALIAS not in device.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.TIF_TV_HOME"):
                raise RuntimeError("Firmware startup does not uniquely resolve to Clean Home")
        data["phase"] = "configured"; save(folder / "snapshot.json", data)
        device.shell("am", "start", "-n", HOME)
        print("Configured. Reboot, then save Clean Home with Always for TV Home. Backups: " + str(folder))
    except Exception:
        data["phase"] = "incomplete-use-rollback"; save(folder / "snapshot.json", data)
        raise RuntimeError("Setup incomplete. Original backup and TV-only reset are saved; run rollback --apply. Do not uninstall the launcher.") from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["inspect", "install", "configure", "rollback"])
    parser.add_argument("--serial", required=True, help="Explicit adb serial, e.g. TV_IP:5555")
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--state-dir", type=Path, default=Path.home() / ".local/share/yasuda-clean-home/devices")
    parser.add_argument("--apk", type=Path, default=ROOT / "dist/Clean-Home-1.4.0.apk")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--power-home", action="store_true")
    parser.add_argument("--smarttube-key", action="store_true")
    args = parser.parse_args()
    device = Device(args.adb, args.serial); model, board, api = device.verified()
    folder = args.state_dir.expanduser().resolve() / device.identity()[:24]
    if args.action == "inspect":
        print(json.dumps({"model": model, "board": board, "api": api, "bluetooth_feature": "feature:android.hardware.bluetooth\n" in device.shell("pm", "list", "features") + "\n", "recovery_baseline_saved": (folder / "snapshot.json").is_file()}, indent=2)); return
    if not args.apply:
        print(f"Plan only: {args.action}; power-home={args.power_home}; SmartTube-key={args.smarttube_key}. Add --apply to execute. Backups remain private outside source control."); return
    args.state_dir.expanduser().mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.action == "install":
        release(args.apk)
        preview = args.state_dir.expanduser() / ("preinstall-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        backup(device, preview)
        device.call("install", "-r", str(args.apk)); device.shell("am", "start", "-n", HOME)
        print("Installed alongside stock. Test navigation, settings and inputs before configure. Snapshot: " + str(preview))
    elif args.action == "configure": configure(device, folder, args)
    else:
        data = json.loads((folder / "snapshot.json").read_text())
        if data["identity"] != device.identity(): raise RuntimeError("Backup belongs to another TV")
        device.root()
        if device.shell("sha256sum", REMOTE + "/restore.sh").split()[0] != data["script_sha"]: raise RuntimeError("Recovery payload changed")
        if "RESTORE_CHECK_OK" not in device.shell("sh", REMOTE + "/restore.sh", "--check"): raise RuntimeError("Recovery preflight failed")
        print("Restoring this TV's saved setup and restarting. Apps/data remain; Clean Home will be disabled.", flush=True)
        try: device.call("shell", f"setsid sh {REMOTE}/restore.sh --apply >{REMOTE}/restore.log 2>&1 </dev/null & wait", timeout=90)
        except (RuntimeError, subprocess.TimeoutExpired): print("Connection ended during restart. Reconnect and check restore.log for RESTORE_APPLY_OK before declaring success.")


if __name__ == "__main__": main()
