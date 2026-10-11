import unittest
from pathlib import Path
import tempfile
from unittest.mock import patch
from prepare import harmony_rows, write_atomic


class PreparationTests(unittest.TestCase):
    def test_inversion_sus4_gap_and_tail_are_preserved(self):
        rows = harmony_rows('"source",0,"D/A"\n"source",1,"N"\n"source",2,"Fsus4"\n"source",3,"N"\n', 4)
        self.assertEqual(rows, [dict(start=0, end=1, root=2, quality='major', bass=9),
                                dict(start=2, end=3, root=5, quality='sus4', bass=None)])

    def test_invalid_time_and_labels_fail_instead_of_guessing(self):
        for data in ['"s",nan,"C"', '"s",1,"Other"', '"s",3,"C"\n"s",2,"C"', '']:
            with self.assertRaises(ValueError):
                harmony_rows(data, 4)

    def test_failed_replace_leaves_previous_result_and_cleans_temporary_file(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)/'analysis.json'
            output.write_text('previous')
            with patch('prepare.os.replace', side_effect=OSError('interrupted')):
                with self.assertRaises(OSError):
                    write_atomic({'melody': [], 'harmony': []}, output)
            self.assertEqual(output.read_text(), 'previous')
            self.assertEqual(list(Path(directory).iterdir()), [output])


if __name__ == '__main__':
    unittest.main()
