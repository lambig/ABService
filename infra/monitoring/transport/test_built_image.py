"""Ensure the transport test runs the exact locally built candidate."""

import json
from pathlib import Path
import unittest
from unittest.mock import patch

import test_transport as transport


class BuiltImageTests(unittest.TestCase):
    def test_both_services_use_the_image_id_without_pull_or_rebuild(self):
        image = 'sha256:' + 'a' * 64
        calls = []

        def command(*args, **kwargs):
            calls.append(args)
            if args[-3:] == ('config', '--format', 'json'):
                return json.dumps({'services': {'translate': {}, 'agent': {}}})
            override = json.loads(Path(args[3]).read_text())
            self.assertEqual(override['services'], {
                'translate': {'image': image}, 'agent': {'image': image}})
            self.assertEqual(args[4:], ('up', '-d', '--pull', 'never', '--no-build'))
            return ''

        with patch.object(transport, 'run', side_effect=command):
            transport.start_transport(['docker', 'compose'], {}, built_image=image)
        self.assertEqual(len(calls), 2)

    def test_mutable_tag_is_rejected_before_startup(self):
        with patch.object(transport, 'run') as run:
            with self.assertRaises(ValueError):
                transport.start_transport(['docker', 'compose'], {}, built_image='agent:latest')
        run.assert_not_called()
