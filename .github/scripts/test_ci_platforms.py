import subprocess
import unittest
from unittest.mock import patch

from ci_platforms import PLATFORMS, platforms_for_paths, select_platforms


class PlatformSelectionTest(unittest.TestCase):
    def test_android_changes_skip_other_hosts(self):
        self.assertEqual(platforms_for_paths([
            "app/src/main/AndroidManifest.xml", "ui/src/androidMain/kotlin/Host.kt",
            "core/src/androidHostTest/kotlin/Test.kt",
        ]), {"android"})

    def test_each_native_host_selects_only_itself(self):
        for path, platform in [
            ("ios_app/HarmonicIos.xcodeproj/project.pbxproj", "ios"),
            ("core/src/iosMain/kotlin/Host.kt", "ios"),
            ("desktop_app/native/macos/Host.m", "desktop"),
            ("ui/src/desktopTest/kotlin/Test.kt", "desktop"),
        ]:
            with self.subTest(path=path):
                self.assertEqual(platforms_for_paths([path]), {platform})

    def test_shared_and_unknown_inputs_run_every_platform(self):
        for path in [
            "core/src/commonMain/kotlin/Model.kt", "ui/src/commonTest/kotlin/Test.kt",
            "resources/src/commonMain/composeResources/drawable/icon.xml",
            "core/src/nativeMain/kotlin/Host.kt", "resources/adblock/list.txt",
            "desktop_app/build.gradle.kts", "local_ai_runtime/build.gradle",
            "settings.gradle", "gradle.properties", "gradle/libs.versions.toml",
            ".github/workflows/push.yml", "new_module/source.kt",
        ]:
            with self.subTest(path=path):
                self.assertEqual(platforms_for_paths([path]), PLATFORMS)

    def test_llama_sources_affect_android_and_desktop(self):
        self.assertEqual(platforms_for_paths([
            "local_ai_runtime/src/main/cpp/CMakeLists.txt",
        ]), {"android", "desktop"})

    def test_docs_and_empty_diffs_skip_packaging(self):
        self.assertEqual(platforms_for_paths(["README.md", "docs/design/image.png"]), set())
        self.assertEqual(platforms_for_paths([]), set())

    def test_manual_and_initial_pushes_run_every_platform(self):
        for event, base in [("workflow_dispatch", "abc"), ("push", ""), ("push", "0" * 40)]:
            self.assertEqual(select_platforms(event, base, "head"), PLATFORMS)

    @patch("ci_platforms.subprocess.run")
    def test_unavailable_comparison_runs_every_platform(self, run):
        run.side_effect = subprocess.CalledProcessError(128, "git")
        self.assertEqual(select_platforms("push", "missing", "head"), PLATFORMS)

    @patch("ci_platforms.subprocess.run")
    def test_renames_and_unusual_filenames_preserve_all_affected_hosts(self, run):
        run.return_value.stdout = b"app/src/old name.kt\0desktop_app/src/new\nname.kt\0"
        self.assertEqual(select_platforms("pull_request", "base", "head"), {"android", "desktop"})
        self.assertIn("--no-renames", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
