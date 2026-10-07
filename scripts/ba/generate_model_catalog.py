#!/usr/bin/env python3
"""Regenerate resource descriptors from saved primary-source metadata; keep reviewed identity bindings.

Fetch the Wiki Models HTML and the pinned GitHub recursive tree separately, then supply their paths.
The script performs no network requests and never guesses a student identity from names.
"""
import argparse
import json
import re
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
    for binding in previous['bindings']:
        if binding['defaultFile'] not in {m['file'] for m in groups[binding['group']]['models']}:
            raise ValueError('A reviewed default disappeared; update the identity binding explicitly')
    return dict(schema=1, revision=revision, bindings=previous['bindings'], groups=parser.groups)


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
    print(f"Preserved {len(result['bindings'])} reviewed identities; generated {len(result['groups'])} resource groups")
