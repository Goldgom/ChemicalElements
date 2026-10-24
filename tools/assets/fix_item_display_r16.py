from pathlib import Path
import json,hashlib
R=Path(__file__).resolve().parents[2]; A=R/'src/main/resources/assets/chemicalelements'
paths=[R/'tools/assets/r16-art-manifest.json',R/'tools/assets/manifest.json']; manifests=[json.loads(p.read_text()) for p in paths]
changed=[]
for block in sorted((A/'models/block').glob('*.json')):
 d=json.loads(block.read_text()); p=A/'models/item'/block.name
 if 'front' not in d.get('textures',{}) or not p.exists():continue
 item=json.loads(p.read_text());item.setdefault('display',{})['gui']={'rotation':[30,225,0],'translation':[0,0,0],'scale':[0.625,0.625,0.625]}
 b=(json.dumps(item,indent=2)+'\n').encode(); p.write_bytes(b);rel=p.relative_to(R).as_posix()
 for m in manifests:m[rel]=hashlib.sha256(b).hexdigest()
 changed.append(p.stem)
assert len(changed)==25,changed
for p,m in zip(paths,manifests):p.write_text(json.dumps(m,indent=2)+'\n')
print(json.dumps({'machine_item_gui_models':changed,'count':len(changed),'world_models_changed':False}))
