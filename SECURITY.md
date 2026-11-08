# Security and private deployment data

Clean Home runs as a normal Android app. Internet permission serves Wikimedia
Commons and, only after reset PIN verification and final confirmation, local
ADB recovery. There is no listening service, analytics or account system.

Each TV receives its own root-owned, checksum-pinned restore script and an
app-private reset configuration. The six-digit PIN verifier uses a random
128-bit salt and PBKDF2-HMAC-SHA256 (120,000 iterations). Five incorrect
attempts lock the launcher reset UI for one minute. The public source and APK
have no universal reset PIN. This PIN protects the launcher UI; someone with root
or authorized ADB access can change it or restore the TV independently.

Recovery restores the recorded setup, disables Clean Home and restarts the TV.
It preserves other apps and personal data. It is not a factory firmware restore,
an erasure of modification history or a warranty eligibility guarantee.

Keep private device snapshots, Android IDs, firmware APKs, reset verifiers,
signing keys, passwords and ADB keys outside the checkout. Do not attach them to
public issues. Deployment defaults to a private directory under the current
user's home. It checks device identity before restoration and refuses to replace
an existing recovery baseline.

Deployment leaves ADB connectivity unchanged. Drop maintenance root with
`adb -s TV_IP:5555 unroot` when finished; this leaves network ADB open. Use the
TV's debug settings to control access. An open, root-capable debug endpoint
permits system changes independently of the launcher's reset PIN.

Report reproducible problems without credentials or device identifiers. For a
sensitive report, contact the repository owner privately through their GitHub
profile before posting details publicly.
