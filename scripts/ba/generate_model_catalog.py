#!/usr/bin/env python3
"""Regenerate resource descriptors from saved primary-source metadata; keep reviewed identity bindings.

Fetch the Wiki Models HTML and the pinned GitHub recursive tree separately, then supply their paths.
The script performs no network requests and never guesses a student identity from names.
"""
import argparse
import csv
import json
import re
import unicodedata
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class ModelsParser(HTMLParser):
    def __init__(self, revision, files):
        super().__init__(convert_charrefs=True)
        self.revision, self.files = revision, files
        self.heading = None
        self.name, self.page = '', ''
        self.groups = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('h2', 'h3'):
            self.heading = []
            self.page = ''
        if self.heading is not None and tag == 'a' and attrs.get('href', '').startswith('/wiki/'):
            self.page = unquote(attrs['href'][6:])
        if 'model-viewer' not in attrs.get('class', '').split() or 'data-models' not in attrs:
            return
        if not self.name or not self.page:
            raise ValueError('Model group has no linked Wiki identity')
        models = []
        for model in json.loads(attrs['data-models']):
            url = urlsplit(model['src'])
            prefix = f'/gh/lihaohong6/BlueArchiveModels@{self.revision}/'
            if url.scheme != 'https' or url.netloc != 'cdn.jsdelivr.net' or not url.path.startswith(prefix):
                raise ValueError('Model source does not match the supplied immutable revision')
            file = unquote(url.path[len(prefix):])
            if '/' in file or '\\' in file or not file.endswith('.glb'):
                raise ValueError('Invalid model path')
            entry = self.files[file]
            models.append(dict(name=model['name'], file=file, bytes=entry['size'], gitBlob=entry['sha']))
        self.groups.append(dict(name=self.name, wikiPage=self.page, models=models,
            viewer={k[5:]: v for k, v in attrs.items() if k.startswith('data-') and k != 'data-models'}))

    def handle_data(self, value):
        if self.heading is not None:
            self.heading.append(value)

    def handle_endtag(self, tag):
        if tag in ('h2', 'h3') and self.heading is not None:
            self.name = ''.join(self.heading).strip()
            self.heading = None


def generate(html, tree, previous, revision):
    if not re.fullmatch('[0-9a-f]{40}', revision) or tree.get('truncated'):
        raise ValueError('Require a complete tree and immutable commit revision')
    files = {x['path']: x for x in tree['tree'] if x['type'] == 'blob' and x['path'].endswith('.glb')}
    parser = ModelsParser(revision, files)
    parser.feed(html)
    names = [g['name'] for g in parser.groups]
    if not names or len(names) != len(set(names)):
        raise ValueError('Missing or duplicate resource groups')
    indexed = {m['file'] for g in parser.groups for m in g['models']}
    if indexed != set(files):
        raise ValueError('Wiki resource list and repository GLB tree differ; review before shipping')
    groups = {g['name']: g for g in parser.groups}
    if previous.get('revision') != revision:
        raise ValueError('Identity review must target the supplied repository revision')
    content_ids, character_ids, dev_ids, aliases = set(), set(), set(), {}
    bindings = []
    for binding in previous['bindings']:
        for key, seen in [('gameKeeContentId', content_ids), ('characterId', character_ids)]:
            value = binding[key]
            if type(value) is not int or value <= 0 or value in seen:
                raise ValueError(f'Invalid or duplicate {key}')
            seen.add(value)
        dev = normalize_development_id(binding['developmentId'])
        if dev in dev_ids:
            raise ValueError('Duplicate development identity')
        dev_ids.add(dev)
        for alias in binding.get('developmentAliases', []):
            alias = normalize_development_id(alias)
            if alias in aliases and aliases[alias] != dev:
                raise ValueError('Conflicting development alias')
            aliases[alias] = dev
        if binding['group'] not in groups:
            raise ValueError('Unknown resource group')
        if binding['defaultFile'] not in {m['file'] for m in groups[binding['group']]['models']}:
            raise ValueError('A reviewed default disappeared; update the identity binding explicitly')
        additional = binding.get('additionalGroups', [])
        if len(additional) != len(set(additional)) or binding['group'] in additional or any(g not in groups for g in additional):
            raise ValueError('Invalid reviewed additional resource group')
        # Keep primary-source review notes outside the APK, while retaining all runtime identities.
        bindings.append({k: binding[k] for k in ('gameKeeContentId', 'characterId', 'developmentId',
            'wikiPage', 'group', 'defaultFile', 'developmentAliases', 'additionalGroups', 'defaultAnimation') if k in binding})
    if any(alias in dev_ids and alias != dev for alias, dev in aliases.items()):
        raise ValueError('Alias shadows a canonical development identity')
    unbound = previous.get('unboundGroups')
    if unbound is None or len({g['group'] for g in unbound}) != len(unbound):
        raise ValueError('Require an explicit review of every unbound resource group')
    represented = {g for b in bindings for g in [b['group'], *b.get('additionalGroups', [])]}
    unbound_names = {g['group'] for g in unbound}
    if represented & unbound_names or represented | unbound_names != set(groups):
        raise ValueError('Every resource group must be bound or explicitly reviewed as unbound')
    for g in unbound:
        if not g.get('reason') or set(g['files']) != {m['file'] for m in groups[g['group']]['models']}:
            raise ValueError('Unbound resource review must account for every file and a reason')
    return dict(schema=1, revision=revision, bindings=bindings, groups=parser.groups)


def normalize_development_id(raw):
    value = unicodedata.normalize('NFKC', raw.strip())
    match = re.fullmatch(r'(CH|NP)[ _-]?(\d{1,4})(?:[ _-](\d{1,2}))?', value, re.I)
    if match:
        prefix, number, form = match.groups()
        return prefix.lower() + number.zfill(4) + ('_' + form.zfill(2) if form else '')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*', value):
        raise ValueError('Invalid development identity')
    return value.lower()


def write_coverage(catalog, output):
    """Account for each exact GLB, including family alternatives that are not a playable costume."""
    with output.open('w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['file', 'group', 'status', 'defaultGameKeeArticles', 'availableFromGameKeeArticles'])
        for group in catalog['groups']:
            family = [b for b in catalog['bindings'] if group['name'] in [b['group'], *b.get('additionalGroups', [])]]
            for model in group['models']:
                defaults = [b for b in family if b['defaultFile'] == model['file']]
                status = 'student-default' if defaults else 'family-alternative' if family else 'explicitly-unbound'
                writer.writerow([model['file'], group['name'], status,
                    ';'.join(str(b['gameKeeContentId']) for b in defaults),
                    ';'.join(str(b['gameKeeContentId']) for b in family)])


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--wiki-html', type=Path, required=True)
    p.add_argument('--tree-json', type=Path, required=True)
    p.add_argument('--bindings-from', type=Path, required=True)
    p.add_argument('--revision', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    result = generate(args.wiki_html.read_text(), json.loads(args.tree_json.read_text()),
        json.loads(args.bindings_from.read_text()), args.revision)
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n')
    write_coverage(result, args.output.with_suffix('.coverage.csv'))
    print(f"Preserved {len(result['bindings'])} reviewed identities; generated {len(result['groups'])} resource groups")
