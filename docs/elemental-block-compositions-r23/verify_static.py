"""Run from project root. Exclusive evidence creation; never rewrites existing reports."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
SCRIPT = ROOT / 'tools/generate_elemental_block_compositions_r23.py'

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def save(name, obj):
    with (OUT / name).open('x', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')

def main():
    for name in ['static-results.json', 'calibration-118.json', 'preservation.json']:
        assert not (OUT / name).exists(), 'Evidence exists, refusing overwrite: ' + name
    baseline = json.loads((OUT / 'baseline.json').read_text())
    before = {p: sha(ROOT / p) for p in baseline['files']}
    assert before == baseline['files'], 'old resources changed since baseline'
    targets = list((ROOT / 'src/main/resources/data/chemicalelements/chemicalelements/item_compositions').glob('elemental_*.json'))
    assert len(targets) == 118
    target_hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in targets}
    check = subprocess.run([sys.executable, str(SCRIPT), '--check'], cwd=ROOT, capture_output=True, text=True)
    assert check.returncode == 0, check.stderr
    refusal = subprocess.run([sys.executable, str(SCRIPT)], cwd=ROOT, capture_output=True, text=True)
    assert refusal.returncode == 2 and 'Refusing to overwrite existing targets; no writes:' in refusal.stderr
    assert all(sha(ROOT / p) == h for p, h in target_hashes.items()), 'refusal modified targets'
    assert all(sha(ROOT / p) == h for p, h in baseline['files'].items()), 'old resources modified'
    staged = subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=ROOT, text=True).splitlines()
    assert sha(ROOT / '.git/index') == baseline['index_sha256'] and staged == baseline['staged_entries'] and len(staged) == 13
    spec = importlib.util.spec_from_file_location('elemental_generator', SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    # Avoid creating __pycache__ outside the allowed source/JSON files.
    sys.dont_write_bytecode = True
    spec.loader.exec_module(mod)
    outputs = mod.expected()
    calibration = []
    for p, doc in outputs.items():
        name = p.stem.removeprefix('elemental_')
        element = mod.read(mod.DATA / 'elements' / (name + '.json'))
        species, amount = next(iter(doc['per_instance'].items()))
        atoms = mod.read(mod.DATA / 'species' / (species.split(':')[1] + '.json'))['atoms']
        calibration.append({'atomic_number': element['atomic_number'], 'element': name,
                            'item_id': doc['match']['items'][0], 'species_id': species,
                            'species_mol_per_item': amount, 'atoms_per_species': atoms[mod.PREFIX + name],
                            'atom_mol_per_item': '1.000000000000000000', 'basis': 'game_convention'})
    save('calibration-118.json', sorted(calibration, key=lambda r: r['atomic_number']))
    save('preservation.json', {'status': 'PASS', 'existing_resource_count': len(before), 'all_old_hashes_unchanged': True,
                              'index_sha256': baseline['index_sha256'], 'staged_entries': staged,
                              'new_elemental_hashes': target_hashes})
    save('static-results.json', {'status': 'PASS', 'check_exit': check.returncode, 'check_result': json.loads(check.stdout),
                                'overwrite_refusal_exit': refusal.returncode, 'refusal_message': refusal.stderr,
                                'refusal_targets_unchanged': True, 'old_resource_hashes_unchanged': len(before),
                                'java_compile_run': False, 'standalone_java_run': False, 'gametest_run': False,
                                'client_run': False, 'screenshots': False})
    print('PASS: 118 exact mappings, refusal exit 2, old resources/index unchanged; Java NOT run')

if __name__ == '__main__':
    main()
