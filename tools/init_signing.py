#!/usr/bin/env python3
"""Keep the private APK signing identity outside the project."""
import os
from pathlib import Path
import secrets
import subprocess

folder = Path(os.environ.get("CLEAN_HOME_SIGNING_DIR", str(Path.home() / ".local/share/yasuda-clean-home/signing")))
folder.mkdir(mode=0o700, parents=True, exist_ok=True)
folder.chmod(0o700)
keystore = folder / "release.jks"
password = folder / "store.password"
if not keystore.exists():
    if not password.exists():
        password.write_text(secrets.token_urlsafe(32))
        password.chmod(0o600)
    java_home = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17"))
    subprocess.run([str(java_home / "bin/keytool"), "-genkeypair", "-keystore", str(keystore), "-alias", "cleanhome", "-storetype", "JKS", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-storepass:file", str(password), "-keypass:file", str(password), "-dname", "CN=Clean Home Personal TV", "-noprompt"], check=True, capture_output=True)
    keystore.chmod(0o600)
if not password.exists(): raise RuntimeError("Signing password is missing; preserve the existing key")
print("Private signing identity is ready outside the project.")
