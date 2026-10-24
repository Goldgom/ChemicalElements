from pathlib import Path
import json,hashlib
r=Path(__file__).resolve().parents[2];res=r/'src/main/resources';checks=0
names=['part_'+n for n in ['port','column_short','column_tall','core','assembly','tower']]+['detector_'+n for n in ['basic','advanced','research']]
files=[]
for name in names:
 p=res/'assets/chemicalelements/models/item'/(name+'.json');j=json.loads(p.read_text());files.append(p);checks+=1
 if name.startswith('part_'):
  for directory in ['blockstates','models/block']:
   p=res/'assets/chemicalelements'/directory/(name+'.json');json.loads(p.read_text());files.append(p);checks+=1
for lang in ['en_us','zh_cn']:
 j=json.loads((res/'assets/chemicalelements/lang'/(lang+'.json')).read_text(encoding='utf-8'))
 for name in names:assert ('block.' if name.startswith('part_') else 'item.')+'chemicalelements.'+name in j;checks+=1
for p in (res/'data/chemicalelements/chemicalelements/parts').glob('*.json'):
 j=json.loads(p.read_text());assert len(j['size'])==3;files.append(p);checks+=1
for p in (res/'data/chemicalelements/chemicalelements/detectors').glob('*.json'):
 j=json.loads(p.read_text());assert j['schema_version']==1;files.append(p);checks+=1
assert len(list((res/'data/chemicalelements/chemicalelements/multiblocks').glob('*.json')))==25;checks+=1
for p in (res/'data/curios').rglob('*.json'):json.loads(p.read_text());files.append(p);checks+=1
base=r/'src/main/java/org/goldgom/chemicalelements'
for pkg in ['machine','network','compat','client']:files+=list((base/pkg).rglob('*.java'))
manifest={str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
o=r/'build/d-evidence/r15-stage2-hashes.json';o.write_text(json.dumps(manifest,indent=2));print(checks,'new resource checks passed');print('manifest SHA256',hashlib.sha256(o.read_bytes()).hexdigest())
