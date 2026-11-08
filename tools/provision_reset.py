#!/usr/bin/env python3
"""Provision a per-device, app-private reset verifier. Never put a PIN in argv."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import subprocess
import tempfile


def verifier(pin):
    if not re.fullmatch(r"[0-9]{6}", pin):
        raise ValueError("Choose exactly six digits")
    salt = secrets.token_bytes(16)
    return {"pinSalt": salt.hex(), "pinHash": hashlib.pbkdf2_hmac("sha256", pin.encode(), salt, 120_000).hex()}


def provision(adb, serial, config):
    def shell(*args):
        return subprocess.run([adb, "-s", serial, "shell", shlex.join(args)], check=True, capture_output=True, text=True, timeout=30).stdout.strip()
    if shell("id", "-u") != "0": raise RuntimeError("Maintenance ADB root is required for provisioning")
    if shell("getprop", "ro.product.board") != "SK708D" or shell("getprop", "ro.build.version.sdk") != "28":
        raise RuntimeError("This restore integration is only reviewed for SK708D/API 28")
    if not re.fullmatch(r"[a-f0-9]{64}", config.get("scriptSha256", "")): raise ValueError("Invalid recovery script digest")
    uid = re.search(r"userId=(\d+)", shell("dumpsys", "package", "local.cleanhome.tv"))
    if not uid: raise RuntimeError("Install Clean Home first")
    data = "/data/user/0/local.cleanhome.tv/files"
    with tempfile.TemporaryDirectory(prefix="cleanhome-config-") as folder:
        file = Path(folder) / "recovery.json"
        file.write_text(json.dumps(config)); file.chmod(0o600)
        subprocess.run([adb, "-s", serial, "push", str(file), "/data/local/tmp/cleanhome-recovery-config.json"], check=True, capture_output=True, timeout=30)
    shell("mkdir", "-p", data)
    shell("cp", "/data/local/tmp/cleanhome-recovery-config.json", data + "/recovery.json")
    shell("chown", uid.group(1) + ":" + uid.group(1), data, data + "/recovery.json")
    shell("chmod", "0700", data)
    shell("chmod", "0600", data + "/recovery.json")
    shell("restorecon", "-R", data)
    # Scrub the transient verifier outside app-private data (not the root restore payload).
    shell("sh", "-c", "cat /dev/null > /data/local/tmp/cleanhome-recovery-config.json; chmod 0600 /data/local/tmp/cleanhome-recovery-config.json")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--spec", type=Path, required=True, help="Private recovery.json with scriptSha256")
    parser.add_argument("--pin-file", type=Path, help="Read PIN from a mode-0600 file instead of prompting")
    args = parser.parse_args()
    if args.pin_file:
        if args.pin_file.stat().st_mode & 0o077: raise ValueError("PIN file must not be accessible to other users")
        pin = args.pin_file.read_text().strip()
    else:
        pin = getpass.getpass("Choose reset PIN (six digits): ")
        if pin != getpass.getpass("Confirm reset PIN: "): raise ValueError("PINs do not match")
    spec = json.loads(args.spec.read_text())
    spec.update(verifier(pin)); pin = None
    provision(args.adb, args.serial, spec)
    print("Per-TV reset protection provisioned. No PIN was printed or embedded in the APK.")


if __name__ == "__main__": main()
