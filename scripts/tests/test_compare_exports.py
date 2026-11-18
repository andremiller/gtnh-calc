import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('compare_exports', ROOT / 'scripts/compare-exports.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ExportComparisonTests(unittest.TestCase):
    def test_reads_mixed_sources_and_remaps(self):
        pack = MODULE.Pack(ROOT / 'src/tests/fixtures/full-export.bin')
        self.assertEqual(4, len(pack.recipes))
        self.assertEqual({'gregtech', 'minecraft', 'thaumcraft'}, set(pack.categories))
        self.assertIn('r:previous-macerator', pack.remaps)
        self.assertEqual(2, sum(r['gt'] is None for r in pack.recipes.values()))

    def test_equal_exports_have_no_differences(self):
        pack = MODULE.Pack(ROOT / 'src/tests/fixtures/direct-export.bin')
        difference = MODULE.difference(pack.recipes, pack.recipes, 5)
        self.assertEqual(0, difference['changed'])
        self.assertEqual(0, difference['onlyReference'])

    def test_reports_added_recipes(self):
        old = MODULE.Pack(ROOT / 'src/tests/fixtures/direct-export.bin')
        new = MODULE.Pack(ROOT / 'src/tests/fixtures/full-export.bin')
        difference = MODULE.difference(old.recipes, new.recipes, 5)
        self.assertEqual(2, difference['onlyCandidate'])
        self.assertEqual(0, difference['onlyReference'])

    def test_rejects_invalid_gzip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'data.bin'
            path.write_bytes(b'invalid')
            with self.assertRaises(OSError):
                MODULE.Pack(path)


if __name__ == '__main__':
    unittest.main()
