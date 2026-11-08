import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
import deploy
from provision_reset import verifier


class DeploymentTests(unittest.TestCase):
    def data(self):
        return {"home": "stock.launcher/.Home", "serialno": "test-serial", "android_id": "test-id", "overlay_mode": "default",
                "power_home": True, "smarttube_key": True, "overlay_sha": "a" * 64,
                "hotkey_original": "b" * 64, "hotkey_patched": "c" * 64,
                "components": {deploy.ALIAS: 0, deploy.FORWARDER: 1}}
    def test_restore_is_guarded_and_restores_recorded_states(self):
        script = deploy.recovery_script(self.data())
        self.assertIn('[ "$(getprop ro.serialno)" = test-serial ]', script)
        self.assertIn('pm default-state --user 0 ' + deploy.ALIAS, script)
        self.assertIn('pm enable --user 0 ' + deploy.FORWARDER, script)
        self.assertIn('sleep 12', script)
        self.assertIn('RESTORE_CHECK_OK', script)
        self.assertNotIn('adb tcpip', script)
        self.assertNotIn('pm uninstall', script)
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "restore.sh"; file.write_text(script)
            subprocess.run(["sh", "-n", str(file)], check=True)
    def test_no_vendor_remount_when_vendor_changes_not_requested(self):
        data = self.data(); data.update(power_home=False, smarttube_key=False)
        self.assertNotIn('remount', deploy.recovery_script(data))
    def test_invalid_digest_is_rejected(self):
        data = self.data(); data["overlay_sha"] = "$(arbitrary)"
        with self.assertRaises(ValueError): deploy.recovery_script(data)
    def test_components_preserve_explicit_and_default_states(self):
        xml = '<package-restrictions><pkg name="local.cleanhome.tv"><enabled-components><item name="local.cleanhome.tv.StartupHome"/></enabled-components></pkg></package-restrictions>'
        self.assertEqual(1, deploy.component_state(xml, deploy.ALIAS))
        with self.assertRaises(ValueError): deploy.component_state(xml, deploy.FORWARDER)
    def test_hotkey_patch_only_changes_dedicated_youtube_aliases(self):
        text = '<keys>' + ''.join('<HotKey key="' + key + '"><Intent package="com.google.android.youtube.tv" class="Old"/></HotKey>' for key in ['KEYCODE_CVT_YOUTUBE', 'KEYCODE_QUICK_START5', 'KEYCODE_OTHER']) + '</keys>'
        patched = deploy.patch_hotkey(text, 'org.example.Splash')
        self.assertEqual(2, patched.count('package="org.smarttube.stable"'))
        self.assertEqual(1, patched.count('package="com.google.android.youtube.tv"'))
        with self.assertRaises(ValueError): deploy.patch_hotkey(patched, 'org.example.Splash')
    def test_pin_verifier_uses_unique_salt_and_no_plaintext(self):
        first = verifier('123456'); second = verifier('123456')
        self.assertNotEqual(first, second)
        self.assertNotIn('123456', first.values())
        self.assertEqual(first['pinHash'], hashlib.pbkdf2_hmac('sha256', b'123456', bytes.fromhex(first['pinSalt']), 120_000).hex())
        with self.assertRaises(ValueError): verifier('short')


if __name__ == "__main__": unittest.main()
