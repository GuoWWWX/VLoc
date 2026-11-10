import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import release_assets


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "output"
        self.arm64 = self.root / "arm64.apk"
        self.arm32 = self.root / "arm32.apk"
        self.previous = self.root / "previous.apk"
        for path in (self.arm64, self.arm32, self.previous):
            path.write_bytes(path.name.encode())
        self.infos = {
            self.arm64: (8, "0.7.0", "arm64-v8a", ("existing-signer",), False),
            self.arm32: (8, "0.7.0", "armeabi-v7a", ("existing-signer",), False),
            self.previous: (7, "0.6.1", "arm64-v8a", ("existing-signer",), False),
        }

    def run_release(self, extra=()):
        argv = ["release_assets.py", str(self.arm64), str(self.arm32), "--sdk", str(self.root),
                "--output", str(self.output), "--previous-release", str(self.previous), *extra]
        with patch("sys.argv", argv), patch.object(release_assets, "apk_info", side_effect=lambda p, _: self.infos[p]), \
                contextlib.redirect_stdout(io.StringIO()):
            release_assets.main()

    def test_same_signer_can_release_without_a_delta(self):
        self.run_release()
        self.assertTrue((self.output / "vloc-update.json").is_file())

    def test_rejects_changed_signer_even_without_a_delta(self):
        self.infos[self.previous] = (7, "0.6.1", "arm64-v8a", ("different-signer",), False)
        with self.assertRaisesRegex(ValueError, "same signer"):
            self.run_release()
        self.assertFalse(self.output.exists())

    def test_rejects_non_increasing_version_code(self):
        self.infos[self.previous] = (8, "0.6.1", "arm64-v8a", ("existing-signer",), False)
        with self.assertRaisesRegex(ValueError, "higher version"):
            self.run_release()
        self.assertFalse(self.output.exists())

    def test_rejects_non_increasing_version_name(self):
        self.infos[self.previous] = (7, "0.7.0", "arm64-v8a", ("existing-signer",), False)
        with self.assertRaisesRegex(ValueError, "higher version"):
            self.run_release()
        self.assertFalse(self.output.exists())

    def test_rejects_mismatched_abi_pair(self):
        self.infos[self.arm32] = self.infos[self.arm64]
        with self.assertRaisesRegex(ValueError, "both ARM ABIs"):
            self.run_release()
        self.assertFalse(self.output.exists())

    def test_rejects_invalid_delta_base_before_writing_assets(self):
        base = self.root / "base.apk"
        self.infos[base] = (6, "0.6.0", "arm64-v8a", ("different-signer",), False)
        with self.assertRaisesRegex(ValueError, "Delta base"):
            self.run_release(["--base", str(base)])
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
