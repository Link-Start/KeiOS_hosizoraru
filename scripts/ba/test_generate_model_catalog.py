"""Offline regression tests for reviewed coverage, variant identities and source integrity."""
import copy
import json
import unittest
from generate_model_catalog import generate, normalize_development_id

REVISION = 'a' * 40
BLOB = 'b' * 40


class ModelCatalogTest(unittest.TestCase):
    def setUp(self):
        self.models = [{'name': 'Default', 'src': f'https://cdn.jsdelivr.net/gh/lihaohong6/BlueArchiveModels@{REVISION}/Student.glb'}]
        self.tree = {'tree': [{'type': 'blob', 'path': 'Student.glb', 'size': 32, 'sha': BLOB}], 'truncated': False}
        self.review = {'revision': REVISION, 'bindings': [{'gameKeeContentId': 10, 'characterId': 10098,
            'developmentId': 'CH0258_02', 'wikiPage': 'Student', 'group': 'Student', 'defaultFile': 'Student.glb',
            'evidence': {'wikiRevision': 42}}], 'unboundGroups': []}

    def html(self):
        models = json.dumps(self.models).replace('"', '&quot;')
        return f'<h2><a href="/wiki/Student">Student</a></h2><div class="model-viewer" data-models="{models}"></div>'

    def build(self):
        return generate(self.html(), self.tree, self.review, REVISION)

    def test_shared_mesh_keeps_both_combat_identities(self):
        attacker = copy.deepcopy(self.review['bindings'][0])
        attacker.update(gameKeeContentId=11, characterId=10099, developmentId='CH0258_01')
        self.review['bindings'].append(attacker)
        result = self.build()
        self.assertEqual(len(result['bindings']), 2)
        self.assertEqual(result['bindings'][0]['defaultFile'], result['bindings'][1]['defaultFile'])
        self.assertNotIn('evidence', result['bindings'][0])

    def test_new_resource_group_requires_explicit_coverage_review(self):
        self.review['bindings'] = []
        with self.assertRaisesRegex(ValueError, 'Every resource group'):
            self.build()
        self.review['unboundGroups'] = [{'group': 'Student', 'files': ['Student.glb'], 'reason': 'NPC'}]
        self.assertEqual(self.build()['bindings'], [])
        self.review['unboundGroups'][0]['files'] = []
        with self.assertRaisesRegex(ValueError, 'every file'):
            self.build()

    def test_duplicate_additional_group_cannot_duplicate_a_model_picker_entry(self):
        self.review['bindings'][0]['additionalGroups'] = ['Student']
        with self.assertRaisesRegex(ValueError, 'additional resource group'):
            self.build()

    def test_revision_tree_and_default_drift_fail_closed(self):
        for mutation in ('revision', 'truncated', 'missing_default', 'extra_file'):
            with self.subTest(mutation=mutation):
                previous, tree = copy.deepcopy(self.review), copy.deepcopy(self.tree)
                if mutation == 'revision': previous['revision'] = 'c' * 40
                if mutation == 'truncated': tree['truncated'] = True
                if mutation == 'missing_default': previous['bindings'][0]['defaultFile'] = 'Missing.glb'
                if mutation == 'extra_file': tree['tree'].append({'type': 'blob', 'path': 'New.glb', 'size': 32, 'sha': BLOB})
                with self.assertRaises(ValueError): generate(self.html(), tree, previous, REVISION)

    def test_duplicate_or_shadowed_student_identity_is_rejected(self):
        duplicate = copy.deepcopy(self.review['bindings'][0])
        duplicate.update(gameKeeContentId=11, characterId=10099, developmentId='CH0258_01')
        self.review['bindings'].append(duplicate)
        self.review['bindings'][0]['developmentAliases'] = ['CH0258_01']
        with self.assertRaisesRegex(ValueError, 'shadows'):
            self.build()
        del self.review['bindings'][0]['developmentAliases']
        duplicate['characterId'] = 10098
        with self.assertRaisesRegex(ValueError, 'duplicate characterId'):
            self.build()

    def test_development_normalization_preserves_form_and_named_variant(self):
        self.assertEqual(normalize_development_id('ＣＨ＿２５８＿２'), 'ch0258_02')
        self.assertNotEqual(normalize_development_id('CH0258_02'), normalize_development_id('CH0258_01'))
        self.assertEqual(normalize_development_id('Hihumi_Swimsuit'), 'hihumi_swimsuit')
        with self.assertRaises(ValueError): normalize_development_id('../CH0258')


if __name__ == '__main__':
    unittest.main()
