from pathlib import Path
import json, hashlib
from refine_structures_r16 import LAYOUTS,SIZES
R=Path(__file__).resolve().parents[2]
checks=0
for name,bays in LAYOUTS.items():
 for turn in range(4):
  seen={(0,0,0)}
  for kind,(x,y,z) in bays:
   sx,sy,sz=SIZES[kind]
   for dx in range(sx):
    for dy in range(sy):
     for dz in range(sz):
      a,b=x+dx,z+dz
      for _ in range(turn): a,b=-b,a
      cell=(a,y+dy,b)
      assert cell not in seen,(name,turn,cell)
      seen.add(cell)
  checks+=1
files=list((R/'src/main/resources/data/chemicalelements/chemicalelements/multiblocks').glob('*.json'))+[R/'src/main/java/org/goldgom/chemicalelements/compat/jade/client/JadeClient.java',R/'tools/assets/r16-art-manifest.json']
out={'version':'r16-art-5-structures-jade','rotated_layout_checks':checks,'art_manifest_sha256':hashlib.sha256((R/'tools/assets/r16-art-manifest.json').read_bytes()).hexdigest(),'files':{p.relative_to(R).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'client_verified':False}
(R/'build/d-evidence/r16-art5-delta-manifest.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps(out,indent=2))
