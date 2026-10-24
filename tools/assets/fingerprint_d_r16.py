from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[2]
files=[]
base=R/'src/main/java/org/goldgom/chemicalelements'
for name in ('machine','client','network','compat'):
 files+=list((base/name).rglob('*.java'))
files+=list((R/'src/main/resources/assets/chemicalelements').rglob('*'))
for rel in ('src/main/resources/data/chemicalelements/chemicalelements/multiblocks','src/main/resources/data/chemicalelements/curios','src/main/resources/data/curios/tags/item'):
 files+=list((R/rel).rglob('*.json'))
entries={p.relative_to(R).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files)) if p.is_file()}
root_hash=hashlib.sha256(json.dumps(entries,sort_keys=True,separators=(',',':')).encode()).hexdigest()
p=R/'build/d-evidence/r16-current-source-fingerprint.json'
p.write_text(json.dumps({'version':'r16-detector-layout-21','source_fingerprint':root_hash,'files':entries,'client_verified':False},indent=2)+'\n')
print(json.dumps({'files':len(entries),'source_fingerprint':root_hash,'output':str(p),'client_verified':False}))
