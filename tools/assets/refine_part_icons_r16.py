"""Apply only after A releases r16-art-3 client capture freeze.
Dedicated miniature assembly item models preserve the six part proportions.
Does not change placed block models or definitions.
"""
from pathlib import Path
import json,hashlib
R=Path(__file__).resolve().parents[2];A=R/'src/main/resources/assets/chemicalelements';manifest=R/'tools/assets/r16-art-manifest.json'
m=json.loads(manifest.read_text());legacy=json.loads((R/'tools/assets/manifest.json').read_text())
parts={'port':(1,1,1),'column_short':(1,2,1),'column_tall':(1,3,1),'core':(2,2,2),'assembly':(3,3,3),'tower':(3,6,3)}
def cuboid(fr,to):
 faces={}
 for direction in ['north','south','east','west','up','down']:
  uv=[fr[0],16-to[1],to[0],16-fr[1]] if direction in ('north','south') else [fr[2],16-to[1],to[2],16-fr[1]] if direction in ('east','west') else [fr[0],fr[2],to[0],to[2]]
  faces[direction]={'texture':'#'+('front' if direction=='north' else 'top' if direction in ('up','down') else 'side'),'uv':uv}
 return {'from':fr,'to':to,'faces':faces}
for name,(sx,sy,sz) in parts.items():
 ident='part_'+name;p=A/'models/item'/f'{ident}.json';rel=p.relative_to(R).as_posix()
 assert hashlib.sha256(p.read_bytes()).hexdigest()==m[rel],f'Unknown modified item model: {p}'
 # Uniform unit size makes column height and core/assembly width legible in inventory.
 unit=2 if name=='tower' else 4
 w,h,d=sx*unit,sy*unit,sz*unit;x=(16-w)/2;z=(16-d)/2;y=(16-h)/2
 shapes=[cuboid([x,y,z],[x+w,y+h,z+d])]
 if name=='port':
  shapes=[cuboid([3,3,4],[13,13,12]),cuboid([5,5,1],[11,11,4]),cuboid([6,6,0],[10,10,1])]
 else:
  # Exposed footings, caps and distinct tier bands, with front control bay inset.
  shapes.extend([cuboid([x-1,y,z-1],[x+w+1,y+1,z+d+1]),cuboid([x-1,y+h-1,z-1],[x+w+1,y+h,z+d+1])])
  if name in ('column_short','column_tall','tower'):
   for tier in range(1,sy):shapes.append(cuboid([x-.5,y+tier*unit-.4,z-.5],[x+w+.5,y+tier*unit+.4,z+d+.5]))
  if name in ('core','assembly'):shapes.append(cuboid([x+1,y+1,z-1],[x+w-1,y+h-1,z]))
  if name=='assembly':
   for xx in (x,x+w-1):shapes.append(cuboid([xx,y,z-1.5],[xx+1,y+h,z-.5]))
 data={'textures':{k:f'chemicalelements:block/{ident}_{k}' for k in ('front','side','top')},'elements':shapes,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.8,.8,.8]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.7,.7,.7]}}}
 b=(json.dumps(data,indent=2)+'\n').encode();backup=R/'build/d-evidence/r16-art3-item-models'/p.name
 if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
 p.write_bytes(b);m[rel]=legacy[rel]=hashlib.sha256(b).hexdigest()
manifest.write_text(json.dumps(m,indent=2)+'\n');(R/'tools/assets/manifest.json').write_text(json.dumps(legacy,indent=2)+'\n')
print('Applied dedicated proportional part icons; six models only. Run verify_r16.py; request A resource sync and new screenshots.')
