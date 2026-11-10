import importlib.machinery
import importlib.util
import io
import json
from pathlib import Path
import shlex
import subprocess
import unittest
from unittest.mock import patch

loader = importlib.machinery.SourceFileLoader("harmonic_dev", str(Path(__file__).parents[1] / "harmonic-dev"))
spec = importlib.util.spec_from_loader(loader.name, loader)
dev = importlib.util.module_from_spec(spec)
loader.exec_module(dev)


class Client:
    def __init__(self):
        self.calls = []
        self.fail_restore = False
        self.fail_delete = False

    def call(self, method, arg=None, value=None):
        self.calls.append((method, arg, value))
        if method == "restore" and self.fail_restore:
            raise RuntimeError("disconnected")
        if method == "delete" and self.fail_delete:
            raise RuntimeError("cleanup disconnected")
        return {"ok": True}


class SettingsCliTest(unittest.TestCase):
    def test_child_failure_restores_and_preserves_exit_code(self):
        client = Client()
        with patch.object(dev.subprocess, "call", return_value=7):
            self.assertEqual(7, dev.run_with_settings(client, ["integrated-browser=false"], ["false"]))
        self.assertEqual(["snapshot", "set", "restore", "delete"], [c[0] for c in client.calls])
        self.assertEqual(client.calls[0][1], client.calls[2][1])

    def test_invalid_override_and_interrupt_still_restore(self):
        client = Client()
        with self.assertRaises(ValueError):
            dev.run_with_settings(client, ["invalid"], ["unused"])
        self.assertEqual(["snapshot", "restore", "delete"], [c[0] for c in client.calls])
        client = Client()
        with patch.object(dev.subprocess, "call", side_effect=KeyboardInterrupt):
            self.assertEqual(130, dev.run_with_settings(client, [], ["unused"]))
        self.assertEqual(["snapshot", "restore", "delete"], [c[0] for c in client.calls])

    def test_failed_restore_retains_snapshot(self):
        client = Client()
        client.fail_restore = True
        with patch.object(dev.subprocess, "call", return_value=0), patch.object(dev.sys, "stderr", new_callable=io.StringIO) as stderr:
            with self.assertRaises(RuntimeError):
                dev.run_with_settings(client, [], ["unused"])
        self.assertIn("Restore failed. Snapshot retained:", stderr.getvalue())
        self.assertNotIn("delete", [c[0] for c in client.calls])

    def test_cleanup_failure_reports_that_settings_were_restored(self):
        client = Client()
        client.fail_delete = True
        with patch.object(dev.subprocess, "call", return_value=0), patch.object(dev.sys, "stderr", new_callable=io.StringIO) as stderr:
            with self.assertRaises(RuntimeError):
                dev.run_with_settings(client, [], ["unused"])
        self.assertIn("Settings restored; snapshot cleanup failed:", stderr.getvalue())
        self.assertEqual(["snapshot", "restore", "delete"], [c[0] for c in client.calls])

    def test_child_argv_and_json_null_are_preserved(self):
        client = Client()
        with patch.object(dev, "SettingsClient", return_value=client), patch.object(dev.subprocess, "call", return_value=0) as child:
            self.assertEqual(0, dev.main(["--device", "emulator-5554", "with-settings", "pref_webview=null", "--", "echo", "a b", "--flag"]))
        child.assert_called_once_with(["echo", "a b", "--flag"])
        self.assertEqual("reset", client.calls[1][0])

    def test_adb_transport_quotes_shell_metacharacters_and_rejects_application_errors(self):
        value = 'a b, $(touch /tmp/never) `whoami` "quotes"\nnext'
        reply = {"ok": True, "value": value}
        with patch.object(dev, "adb_path", return_value="adb"):
            client = dev.SettingsClient("emulator-5554", "com.example")
        with patch.object(dev.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "Result: Bundle[{json=" + json.dumps(reply) + "}]\n", "")) as run:
            self.assertEqual(reply, client.call("set", "pref_string", value))
        argv = run.call_args.args[0]
        self.assertEqual(["adb", "-s", "emulator-5554", "shell"], argv[:4])
        tokens = shlex.split(argv[4])
        self.assertEqual("value:s:" + json.dumps(value, ensure_ascii=False), tokens[-1])
        with patch.object(dev.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, 'Result: Bundle[{json={"ok":false,"error":"bad value"}}]', "")):
            with self.assertRaisesRegex(RuntimeError, "bad value"):
                client.call("set", "pref_webview", "bad")


if __name__ == "__main__":
    unittest.main()
