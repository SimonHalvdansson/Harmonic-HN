from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

from package_windows_arm64 import package, require_arm64_pe, verify_image


def executable(path, machine=0xAA64):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = bytearray(0x86)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 0x3C, 0x80)
    struct.pack_into('<4sH', data, 0x80, b'PE\0\0', machine)
    path.write_bytes(data)


class WindowsArm64PackageTest(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'win32', 'Inno Setup runs on Windows')
    def test_real_inno_compiler_accepts_the_installer_template(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            image = root / 'native image'
            for name in ['Harmonic.exe', 'runtime/bin/java.exe', 'runtime/bin/server/jvm.dll']:
                executable(image / name)
            (image / 'app').mkdir()
            (image / 'app/app.jsa').touch()
            metadata = root / 'metadata.properties'
            metadata.write_text('versionName=3.2.1\n')
            installer = package(image, metadata, root / 'installer')
            self.assertGreater(installer.stat().st_size, 0)

    def test_x64_emulation_cannot_silently_pass_as_a_native_build(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'Harmonic.exe'
            executable(path)
            require_arm64_pe(path)
            executable(path, 0x8664)
            with self.assertRaisesRegex(ValueError, 'ARM64'):
                require_arm64_pe(path)

    def test_entire_runtime_must_match_the_launcher_architecture(self):
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory)
            for name in ['Harmonic.exe', 'runtime/bin/java.exe', 'runtime/bin/server/jvm.dll']:
                executable(image / name)
            (image / 'app').mkdir()
            (image / 'app/app.jsa').touch()
            verify_image(image)
            executable(image / 'runtime/bin/server/jvm.dll', 0x8664)
            with self.assertRaisesRegex(ValueError, 'ARM64'):
                verify_image(image)

    def test_compiler_receives_literal_paths_with_spaces_and_the_embedded_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            image = root / 'image with spaces'
            for name in ['Harmonic.exe', 'runtime/bin/java.exe', 'runtime/bin/server/jvm.dll']:
                executable(image / name)
            (image / 'app').mkdir()
            (image / 'app/app.jsa').touch()
            metadata = root / 'metadata.properties'
            metadata.write_text('versionName=3.2.1\n')
            destination = root / 'output'
            def compile_installer(command, **kwargs):
                self.assertIn(f'/DAppImage={image.resolve()}', command)
                self.assertIn('/DAppVersion=3.2.1', command)
                self.assertTrue(kwargs['check'])
                (destination / 'Harmonic-3.2.1-windows-arm64.exe').write_bytes(b'installer')
            with patch('package_windows_arm64.subprocess.run', side_effect=compile_installer):
                result = package(image, metadata, destination, compiler='ISCC.exe')
            self.assertEqual(result.name, 'Harmonic-3.2.1-windows-arm64.exe')


if __name__ == '__main__':
    unittest.main()
