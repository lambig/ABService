import unittest
import json
import io
import os
from contextlib import redirect_stderr
from pathlib import Path
import tempfile
from unittest.mock import patch
from prepare import DEPENDENCIES, harmony_rows, main, write_atomic


class PreparationTests(unittest.TestCase):
    def test_output_cannot_replace_any_input_or_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            audio, config = root/'audio.wav', root/'config.json'
            paths = {key: root/key for key in DEPENDENCIES}
            for path in [audio, *paths.values()]:
                path.write_bytes(b'original input')
            config.write_text(json.dumps({key: str(path) for key, path in paths.items()}))
            originals = {path: path.read_bytes() for path in [audio, config, *paths.values()]}
            for output in originals:
                with self.subTest(output=output.name), patch('prepare.generate') as generate, redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as error:
                        main(['--audio', str(audio), '--config', str(config), '--output', str(output)])
                    self.assertEqual(error.exception.code, 2)
                    generate.assert_not_called()
                    self.assertEqual({path: path.read_bytes() for path in originals}, originals)
            alias = root/'weights-alias'
            os.link(paths['weights'], alias)
            with patch('prepare.generate') as generate, redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    main(['--audio', str(audio), '--config', str(config), '--output', str(alias)])
                generate.assert_not_called()
                self.assertEqual(alias.read_bytes(), b'original input')

    def test_separate_output_is_written_without_modifying_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            audio, config, output = root/'audio.wav', root/'config.json', root/'analysis.json'
            paths = {key: root/key for key in DEPENDENCIES}
            for path in [audio, *paths.values()]:
                path.write_bytes(b'original input')
            config.write_text(json.dumps({key: str(path) for key, path in paths.items()}))
            originals = {path: path.read_bytes() for path in [audio, config, *paths.values()]}
            value = {'melody': [], 'harmony': []}
            with patch('prepare.generate', return_value=value):
                main(['--audio', str(audio), '--config', str(config), '--output', str(output)])
            self.assertEqual(json.loads(output.read_text()), value)
            self.assertEqual({path: path.read_bytes() for path in originals}, originals)

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
