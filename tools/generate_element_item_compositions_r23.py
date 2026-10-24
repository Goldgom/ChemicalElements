"""118 default samples: 1 atom-mol/item, a game calibration, NOT a phase model.
Run from this project: python tools/generate_element_item_compositions_r23.py
Read-only validation: add --check. Never overwrites even identical existing files.
"""
import argparse
import csv
import hashlib
import json
import re
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/chemicalelements/chemicalelements'
PREFIX = 'chemicalelements:'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def expected():
    source = (ROOT / 'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text(encoding='utf-8')
    names = re.findall(r'"([a-z]+)"', re.search(r'NAMES=List\.of\((.*?)\);', source, re.S).group(1))
    assert len(names) == len(set(names)) == 118, 'registered names must be exactly 118'
    with (ROOT / 'docs/material-phase-r23/coverage-118.csv').open(encoding='utf-8-sig', newline='') as f:
        matrix = list(csv.DictReader(f))
    assert {r['element'] for r in matrix} == set(names), 'preparation matrix mismatch'
    elements = {p.stem: read(p) for p in (DATA / 'elements').glob('*.json')}
    assert set(elements) == set(names)
    assert {v['atomic_number'] for v in elements.values()} == set(range(1, 119))
    outputs = {}
    for name in names:
        molecular = name in ('hydrogen', 'oxygen')
        species = name + '_gas' if molecular else name
        definition = read(DATA / 'species' / (species + '.json'))
        factor = 2 if molecular else 1
        assert definition['atoms'] == {PREFIX + name: factor} and definition['charge'] == 0
        amount = '0.500000000000000000' if molecular else '1.000000000000000000'
        assert Decimal(amount) * factor == 1
        note = ('Uses existing diatomic species: 0.5 molecule-mol x 2 = 1 atom-mol per item. '
                if molecular else 'Uses existing atomic bookkeeping species: 1 species-mol x 1 = 1 atom-mol per item. ')
        doc = {'schema_version': 1, 'match': {'items': [PREFIX + 'element_' + name]},
               'priority': 100, 'per_instance': {PREFIX + species: amount},
               'basis': 'game_convention',
               'notes': note + 'Sample quantity is a game calibration, not measured item mass. Species phase does not establish current sample phase, stability, allotrope or transition behavior; no thermal properties are supplied.',
               'source': 'ChemicalElements element-item calibration r23: one atom-mol per item; existing bundled species stoichiometry retained.'}
        outputs[DATA / 'item_compositions' / ('element_' + name + '.json')] = doc
    return outputs


def validate(outputs):
    targets = set(outputs)
    actual = set((DATA / 'item_compositions').glob('element_*.json'))
    assert actual == targets, 'missing or extra element mapping files'
    item_ids = {d['match']['items'][0] for d in outputs.values()}
    for path, wanted in outputs.items():
        doc = read(path)
        assert doc == wanted, 'schema/calibration mismatch: ' + str(path)
        assert set(doc) == {'schema_version', 'match', 'priority', 'per_instance', 'basis', 'notes', 'source'}
        for species, amount in doc['per_instance'].items():
            assert isinstance(amount, str) and len(amount) <= 80
            n = Decimal(amount)
            assert n > 0 and n.as_tuple().exponent >= -18 and (n * 10**18) == (n * 10**18).to_integral_value()
            atoms = read(DATA / 'species' / (species.split(':')[1] + '.json'))['atoms']
            assert len(atoms) == 1 and n * next(iter(atoms.values())) == 1
    # Existing vanilla mappings remain read-only; reject collisions at ANY priority.
    others = 0
    for path in (DATA / 'item_compositions').glob('*.json'):
        if path in targets:
            continue
        doc = read(path)
        assert not item_ids.intersection(doc['match']['items']), 'existing item match collision: ' + str(path)
        others += 1
    return {'status': 'PASS', 'element_files': len(outputs), 'other_mapping_files': others,
            'atom_mol_per_item': '1', 'molecular_species': ['hydrogen_gas', 'oxygen_gas'],
            'atomic_bookkeeping_species': 116, 'compile_run': False, 'gametest_run': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='read-only validation; no writes')
    args = parser.parse_args()
    outputs = expected()
    if not args.check:
        conflicts = [str(p.relative_to(ROOT)) for p in outputs if p.exists()]
        if conflicts:
            parser.exit(2, 'Refusing to overwrite existing targets (no files written): ' + ', '.join(conflicts) + '\n')
        # Check all foreign matches before the first write.
        wanted = {d['match']['items'][0] for d in outputs.values()}
        for p in (DATA / 'item_compositions').glob('*.json'):
            assert not wanted.intersection(read(p)['match']['items']), 'existing match collision: ' + str(p)
        for path, doc in outputs.items():
            with path.open('x', encoding='utf-8', newline='\n') as f:
                f.write(json.dumps(doc, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps(validate(outputs), indent=2))


if __name__ == '__main__':
    main()
