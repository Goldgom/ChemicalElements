"""Elemental BlockItem calibration: 1 atom-mol/item, NOT 1 m3 of dense matter.
Run once without arguments; --check validates read-only. Never overwrites.
"""
import argparse
import hashlib
import json
import re
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/chemicalelements/chemicalelements'
PREFIX = 'chemicalelements:'
CONTENT_MANIFEST_HASH = '52514e22cf2cde25afb07c3cd760aa2a5b2bc631f640215cd65332915cf22a6f'


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def expected():
    manifest = ROOT / 'docs/material-content-r23/manifest.json'
    assert hashlib.sha256(manifest.read_bytes()).hexdigest() == CONTENT_MANIFEST_HASH, 'content manifest version changed'
    entries = {r['path']: r['sha256'] for r in read(manifest)['files']}
    registration = 'src/main/java/org/goldgom/chemicalelements/registry/ElementMaterials.java'
    source = (ROOT / registration).read_text(encoding='utf-8')
    assert hashlib.sha256((ROOT / registration).read_bytes()).hexdigest() == entries[registration], 'registration changed'
    assert 'for (String name : ElementItems.NAMES) add("elemental_" + name, blocks, items);' in source
    assert 'new MaterialBlockItem(block.get(), new Item.Properties(), id(path))' in source
    names_source = (ROOT / 'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text(encoding='utf-8')
    names = re.findall(r'"([a-z]+)"', re.search(r'NAMES\s*=\s*List\.of\((.*?)\);', names_source, re.S).group(1))
    elements = {p.stem: read(p) for p in (DATA / 'elements').glob('*.json')}
    assert len(names) == len(set(names)) == 118 and set(names) == set(elements)
    assert {e['atomic_number'] for e in elements.values()} == set(range(1, 119))
    outputs = {}
    for name in names:
        molecular = name in ('hydrogen', 'oxygen')
        species = name + '_gas' if molecular else name
        amount = '0.500000000000000000' if molecular else '1.000000000000000000'
        definition = read(DATA / 'species' / (species + '.json'))
        assert definition['atoms'] == {PREFIX + name: 2 if molecular else 1} and definition['charge'] == 0
        assert Decimal(amount) * next(iter(definition['atoms'].values())) == 1
        old = read(DATA / 'item_compositions' / ('element_' + name + '.json'))
        assert old['schema_version'] == 1 and old['match']['items'] == [PREFIX + 'element_' + name]
        assert old['per_instance'] == {PREFIX + species: amount}, 'old calibration mismatch: ' + name
        outputs[DATA / 'item_compositions' / ('elemental_' + name + '.json')] = {
            'schema_version': 1, 'match': {'items': [PREFIX + 'elemental_' + name]},
            'priority': 100, 'per_instance': {PREFIX + species: amount}, 'basis': 'game_convention',
            'notes': ('Existing diatomic species: 0.5 molecule-mol x 2 = 1 atom-mol per item. ' if molecular else
                      'Existing atomic bookkeeping species: 1 species-mol x 1 = 1 atom-mol per item. ') +
                     'Explicit game calibration, not 1 m3 of dense pure element, measured density, actual physical phase, stability or allotrope. Geometric block volume does not set matter quantity. No thermal properties or placement/pickup permission supplied.',
            'source': 'ChemicalElements elemental-block calibration r23: 1 atom-mol/item; existing species and element-item calibration retained.'}
    return outputs


def validate(outputs):
    targets = set(outputs)
    assert set((DATA / 'item_compositions').glob('elemental_*.json')) == targets
    ids = {d['match']['items'][0] for d in outputs.values()}
    for p, doc in outputs.items():
        assert read(p) == doc, 'unexpected content: ' + str(p)
        assert set(doc['match']) == {'items'} and len(doc['match']['items']) == 1
        for species, amount in doc['per_instance'].items():
            assert isinstance(amount, str) and Decimal(amount).as_tuple().exponent == -18
            atoms = read(DATA / 'species' / (species.split(':')[1] + '.json'))['atoms']
            assert Decimal(amount) * next(iter(atoms.values())) == 1
    others = 0
    for p in (DATA / 'item_compositions').glob('*.json'):
        if p in targets:
            continue
        matches = read(p)['match']['items']
        assert not ids.intersection(matches), 'duplicate elemental match: ' + str(p)
        assert not any(i.startswith(PREFIX + 'mixture_carrier_') for i in matches), 'generic carrier has invented default: ' + str(p)
        others += 1
    return {'status': 'PASS', 'elemental_files': 118, 'other_mapping_files': others,
            'atomic_species': 116, 'diatomic_species': ['hydrogen_gas', 'oxygen_gas'],
            'atom_mol_per_item': '1', 'java_compiled': False, 'java_tests_run': False, 'gametest_run': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='read-only; never changes files')
    args = parser.parse_args()
    outputs = expected()
    if not args.check:
        conflicts = [str(p.relative_to(ROOT)) for p in outputs if p.exists()]
        if conflicts:
            parser.exit(2, 'Refusing to overwrite existing targets; no writes: ' + ', '.join(conflicts) + '\n')
        wanted = {d['match']['items'][0] for d in outputs.values()}
        for p in (DATA / 'item_compositions').glob('*.json'):
            assert not wanted.intersection(read(p)['match']['items']), 'existing match collision: ' + str(p)
        # Exclusive create also handles a race after preflight; no deletion/rollback on partial failure.
        for p, doc in outputs.items():
            with p.open('x', encoding='utf-8', newline='\n') as f:
                f.write(json.dumps(doc, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps(validate(outputs), indent=2))


if __name__ == '__main__':
    main()
