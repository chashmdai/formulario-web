import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('storage', Path(__file__).resolve().parents[1] / 'linux/media-storage-report.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class StorageReport(unittest.TestCase):
    def store(self, root):
        for name in mod.FOLDERS:
            (root / name).mkdir()
        return root

    def test_inventory_counts_without_modifying_content(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.store(Path(tmp))
            f = root / 'objects/private-name'
            f.write_bytes(b'original')
            os.chmod(f, 0o400)
            before = f.stat()
            report = mod.inventory(root)
            self.assertEqual(report['carpetas']['objects']['bytesLogicos'], 8)
            self.assertEqual(report['carpetas']['objects']['archivos'], 1)
            self.assertNotIn('private-name', str(report))
            self.assertEqual(before.st_mtime_ns, f.stat().st_mtime_ns)
            self.assertEqual(f.read_bytes(), b'original')

    def test_symlink_and_incomplete_store_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.store(Path(tmp))
            (root / 'incoming/link').symlink_to(root / 'objects')
            with self.assertRaises(ValueError):
                mod.inventory(root)
            (root / 'incoming/link').unlink()
            (root / 'chunks').rmdir()
            with self.assertRaises(ValueError):
                mod.inventory(root)

    def test_sparse_size_is_not_allocated_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.store(Path(tmp))
            with (root / 'incoming/sparse').open('wb') as file:
                file.truncate(1024 * 1024)
            result = mod.inventory(root)['carpetas']['incoming']
            self.assertEqual(result['bytesLogicos'], 1024 * 1024)
            self.assertLessEqual(result['bytesAsignados'], result['bytesLogicos'])

    def test_alerts_quota_space_and_missing_measurement(self):
        report = dict(filesystem=dict(libreAplicacion=5 * mod.GIB))
        self.assertEqual(mod.alerts(report, 10 * mod.GIB, 4 * mod.GIB), ['RESERVAS_NO_MEDIDAS'])
        report['mysql'] = dict(reservados=8 * mod.GIB, discrepanciaReserva=False)
        self.assertEqual(mod.alerts(report, 10 * mod.GIB, 4 * mod.GIB), ['CUOTA_PREVENTIVA'])
        self.assertEqual(report['mysql']['disponibleCuota'], 2 * mod.GIB)
        report['filesystem']['libreAplicacion'] = mod.GIB
        report['mysql']['reservados'] = 10 * mod.GIB
        self.assertEqual(mod.alerts(report, 10 * mod.GIB, 4 * mod.GIB), ['ESPACIO_CRITICO', 'CUOTA_CRITICA'])
        report['mysql']['discrepanciaReserva'] = True
        self.assertEqual(mod.alerts(report, 10 * mod.GIB, 4 * mod.GIB), ['CONTABILIDAD_INCONSISTENTE', 'ESPACIO_CRITICO', 'CUOTA_CRITICA'])

    def test_sql_identifiers_rejected_before_subprocess(self):
        with patch.object(mod.subprocess, 'run') as run:
            with self.assertRaises(ValueError):
                mod.database_summary('a;delete', 'container')
            run.assert_not_called()

    def test_database_queries_read_only_and_aggregated(self):
        from types import SimpleNamespace
        response = SimpleNamespace(returncode=0, stdout='100\n100\nVALIDADO\t1\t40\t100\n')
        with patch.object(mod.subprocess, 'run', return_value=response) as run:
            result = mod.database_summary('synthetic', 'container')
            query = run.call_args.kwargs['input']
            self.assertIn('SET TRANSACTION READ ONLY', query)
            self.assertNotIn('DELETE', query)
            self.assertNotIn('UPDATE', query)
            self.assertFalse(result['discrepanciaReserva'])
            self.assertEqual(result['reservados'], 100)

    def test_invalid_thresholds(self):
        with self.assertRaises(ValueError):
            mod.alerts({}, 0, 1)


if __name__ == '__main__':
    unittest.main()