"""Read-only status13 contract checks; source pixels are NOT client evidence."""
from pathlib import Path
import hashlib,json,struct,zlib
R=Path(__file__).resolve().parents[2]
A=R/'src/main/resources/assets/chemicalelements'
def pixels(p):
 b=p.read_bytes();w,h=struct.unpack('>II',b[16:24]);i=8;compressed=b''
 while i<len(b):
  n=int.from_bytes(b[i:i+4],'big')
  if b[i+4:i+8]==b'IDAT':compressed+=b[i+8:i+8+n]
  i+=n+12
 raw=zlib.decompress(compressed);stride=w*4+1
 assert (w,h)==(32,32) and all(raw[y*stride]==0 for y in range(h)),p
 return lambda x,y:tuple(raw[y*stride+1+x*4:y*stride+5+x*4])
manifest=json.loads((R/'tools/assets/r16-art-manifest.json').read_text())
colors={1:(54,226,206,255),2:(248,184,62,255),3:(255,102,77,255)}
rotations={'north':0,'east':90,'south':180,'west':270}
names=[];checks=0
for model in sorted((A/'models/block').glob('*.json')):
 j=json.loads(model.read_text())
 if 'front' not in j.get('textures',{}) or not model.with_name(model.stem+'_state_3.json').exists():continue
 name=model.stem;names.append(name)
 variants=json.loads((A/'blockstates'/f'{name}.json').read_text())['variants']
 for state,color in colors.items():
  p=A/'textures/block'/f'{name}_state_{state}.png';px=pixels(p)
  assert hashlib.sha256(p.read_bytes()).hexdigest()==manifest[p.relative_to(R).as_posix()],p
  for x,y in ((3,4),(28,5),(3,25),(28,24),(4,15),(27,15)):
   assert px(x,y)==color,(name,state,'perimeter',x,y)
  expected={(13,21),(15,24)} if state==1 else ({(12,21),(18,21),(14,27),(20,27)} if state==2 else {(12,21),(19,21),(15,24),(16,25)})
  for x,y in expected:assert px(x,y)==color,(name,state,'symbol',x,y)
  sm=json.loads(model.with_name(f'{name}_state_{state}.json').read_text())
  assert sm['textures']['front']==f'chemicalelements:block/{name}_state_{state}'
  for facing,rotation in rotations.items():
   keys=[k for k in variants if dict(v.split('=') for v in k.split(','))=={'facing':facing,'visual':str(state)}]
   assert len(keys)==1,(name,state,facing)
   entry=variants[keys[0]]
   assert entry['model']==f'chemicalelements:block/{name}_state_{state}'
   assert entry.get('y',0)==rotation,(name,state,facing,entry)
   checks+=1
assert len(names)==25,names
fingerprint=json.loads((R/'build/d-evidence/r16-current-source-fingerprint.json').read_text())
print(json.dumps({'machines':len(names),'direction_state_checks':checks,'pixel_color_shape_checks':'passed','source_fingerprint_metadata':{k:v for k,v in fingerprint.items() if not isinstance(v,(dict,list))},'client_verified':False,'mutates_game_assets':False}))
