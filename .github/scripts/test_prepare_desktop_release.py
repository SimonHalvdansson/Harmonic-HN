from pathlib import Path
import tempfile
import unittest

from prepare_desktop_release import EXTENSIONS, prepare


class DesktopReleaseFilenameTest(unittest.TestCase):
    def test_each_platform_keeps_its_contents_and_uses_the_embedded_version(self):
        for platform, extension in EXTENSIONS.items():
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                package = root / f"original{extension}"
                package.write_bytes(b"installer contents")
                metadata = root / "metadata.properties"
                metadata.write_text("# generated\nversionName=3.2.1\nversionCode=99\n")
                output = prepare(str(package), platform, metadata, root / "public")
                self.assertEqual(output.name, f"Harmonic-3.2.1-{platform}{extension}")
                self.assertEqual(output.read_bytes(), package.read_bytes())

    def test_missing_or_ambiguous_packages_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for count in (0, 2):
                for i in range(count):
                    (root / f"package{i}.deb").touch()
                with self.assertRaises(ValueError):
                    prepare(str(root / "*.deb"), "linux", root / "unused", root / "public")

    def test_existing_release_file_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            package = root / "original.deb"
            package.write_bytes(b"installer contents")
            metadata = root / "metadata.properties"
            metadata.write_text("versionName=3.2.1\n")
            prepare(str(package), "linux", metadata, root / "public")
            with self.assertRaises(FileExistsError):
                prepare(str(package), "linux", metadata, root / "public")


if __name__ == "__main__":
    unittest.main()
