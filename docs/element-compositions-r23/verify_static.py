"""Evidence only; no Java/Gradle, never rewrites existing evidence files."""
from pathlib import Path
import csv, hashlib, json, subprocess, sys
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
DATA = ROOT / 'src/main/resources/data/chemicalelements/chemicalelements'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def write(name, obj):
    with (OUT/name).open('x', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2); f.write('\n')
files = sorted((DATA/'item_compositions').glob('element_*.json'))
before = {str(p.relative_to(ROOT)): sha(p) for p in files}
run = subprocess.run([sys.executable, 'tools/generate_element_item_compositions_r23.py', '--check'], cwd=ROOT, capture_output=True, text=True)
assert run.returncode == 0, run.stderr
refused = subprocess.run([sys.executable, 'tools/generate_element_item_compositions_r23.py'], cwd=ROOT, capture_output=True, text=True)
assert refused.returncode == 2 and 'Refusing to overwrite' in refused.stderr
assert before == {str(p.relative_to(ROOT)): sha(p) for p in files}
prep = json.loads((ROOT/'docs/material-phase-r23/manifest.json').read_text(encoding='utf-8-sig'))
protected = [e for e in prep['inputs'] if '/species/' in e['path'].replace('\\','/') or '/elements/' in e['path'].replace('\\','/')]
assert len(protected) == 256
assert all(sha(ROOT/e['path']) == e['sha256'].upper() for e in protected)
index = sha(ROOT/'.git/index')
assert index == '82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69'
staged = subprocess.run(['git', 'diff', '--cached', '--name-only'], cwd=ROOT, capture_output=True, text=True, check=True).stdout.splitlines()
assert len(staged) == 13
rows=[]
for p in files:
    d=json.loads(p.read_text(encoding='utf-8')); species, amount=next(iter(d['per_instance'].items()))
    s=json.loads((DATA/'species'/(species.split(':')[1]+'.json')).read_text(encoding='utf-8'))
    element, factor=next(iter(s['atoms'].items()))
    e=json.loads((DATA/'elements'/(element.split(':')[1]+'.json')).read_text(encoding='utf-8'))
    rows.append({'atomic_number':e['atomic_number'],'item':d['match']['items'][0], 'species':species,'species_mol':amount,'atoms_per_species':factor,'atom_mol_per_item':'1.000000000000000000','species_phase_not_sample_phase':s['phase'],'basis':'game_convention','reason':d['notes']})
rows.sort(key=lambda r:r['atomic_number'])
write('calibration-118.json',rows)
with (OUT/'calibration-118.csv').open('x',encoding='utf-8',newline='') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
write('static-results.json',{'check_exit':run.returncode,'check_stdout':run.stdout,'overwrite_refusal_exit':refused.returncode,'overwrite_refusal_message_prefix':refused.stderr[:120],'118_output_hashes_unchanged_after_refusal':True,'existing_elements_species_matching_preparation':len(protected),'git_index_sha256':index,'staged_entries':staged,'compile':'NOT_RUN','java_standalone_test':'NOT_RUN','gametest':'NOT_RUN'})
write('existing-resources-hashes.json',[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':sha(p)} for section in ('elements','species','item_compositions') for p in sorted((DATA/section).glob('*.json')) if p not in files])
outputs=files+[ROOT/'tools/generate_element_item_compositions_r23.py',ROOT/'src/main/java/org/goldgom/chemicalelements/gametest/core/ElementItemCompositionGameTests.java',ROOT/'src/test/java/org/goldgom/chemicalelements/data/ElementItemCompositionTest.java']+sorted(p for p in OUT.iterdir() if p.is_file() and p.name!='manifest.json')
write('manifest.json',{'task':'chem-element-compositions-r23','integration':'chem-integration-execute-r22-next NEXT BATCH ONLY; not fixed 2693 snapshot','files':[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':sha(p)} for p in outputs]})
manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
assert all(sha(ROOT/e['path'])==e['sha256'] for e in manifest['files'])
print(json.dumps({'status':'PASS','manifest_files':len(manifest['files']),'manifest_sha256':sha(OUT/'manifest.json'),'protected_elements_species':len(protected),'staged_entries':len(staged),'no_compile_or_gametest':True}))
