from pathlib import Path
import json,hashlib,struct,zlib
R=Path(__file__).resolve().parents[2];A=R/'src/main/resources/assets/chemicalelements';E=R/'build/d-evidence'
errors=[];hashes={};textures=list((A/'textures').rglob('*.png'))
def decode(p):
 b=p.read_bytes();w,h=struct.unpack('>II',b[16:24]);idx=8;data=b''
 while idx<len(b):
  n=struct.unpack('>I',b[idx:idx+4])[0];t=b[idx+4:idx+8]
  if t==b'IDAT':data+=b[idx+8:idx+8+n]
  idx+=n+12
 raw=zlib.decompress(data);stride=w*4+1
 assert all(raw[y*stride]==0 for y in range(h)),p
 return w,h,[list(raw[y*stride+1:(y+1)*stride]) for y in range(h)]
for p in textures:
 w,h,rows=decode(p);colors=set(tuple(row[i:i+4]) for row in rows for i in range(0,len(row),4))
 if len(colors)<5:errors.append('blank/flat '+str(p))
 digest=hashlib.sha256(p.read_bytes()).hexdigest()
 if digest in hashes:errors.append('duplicate '+str(p)+' '+hashes[digest])
 hashes[digest]=str(p)
for p in (A/'models').rglob('*.json'):
 j=json.loads(p.read_text())
 for t in j.get('textures',{}).values():
  if t.startswith('chemicalelements:') and not (A/'textures'/(t.split(':')[1]+'.png')).exists():errors.append('missing texture '+t+' '+str(p))
  if t.startswith('minecraft:'):errors.append('vanilla texture '+str(p))
 parent=j.get('parent','')
 if parent.startswith('chemicalelements:') and not (A/'models'/(parent.split(':')[1]+'.json')).exists():errors.append('missing parent '+str(p))
 for box in j.get('elements',[]):
  for f in box['faces'].values():
   if len(f.get('uv',[]))!=4 or any(v<0 or v>16 for v in f.get('uv',[])):errors.append('invalid UV '+str(p))
def resolved_textures(p,seen=None):
 seen=set() if seen is None else seen
 if p in seen:raise ValueError('cyclic model parent '+str(p))
 seen.add(p);j=json.loads(p.read_text());parent=j.get('parent','');result={}
 if parent.startswith('chemicalelements:'):result.update(resolved_textures(A/'models'/(parent.split(':')[1]+'.json'),seen))
 result.update(j.get('textures',{}));return result
for p in (A/'models').rglob('*.json'):
 j=json.loads(p.read_text());textures_by_key=resolved_textures(p)
 for b in j.get('elements',[]):
  if any(b['from'][i]>=b['to'][i] for i in range(3)):errors.append('inverted cuboid '+str(p))
  for face in b['faces'].values():
   ref=face['texture'];visited=set()
   while ref.startswith('#'):
    key=ref[1:]
    if key in visited or key not in textures_by_key:errors.append('unresolved texture '+str(p)+' '+ref);break
    visited.add(key);ref=textures_by_key[key]
for p in (A/'blockstates').glob('*.json'):
 variants=json.loads(p.read_text()).get('variants',{})
 if p.stem.startswith('part_') and len(variants)!=216:errors.append('incomplete part state domain '+str(p))
 if p.stem not in ('example_block',) and not p.stem.startswith('part_') and len(variants)!=16:errors.append('incomplete facing/visual domain '+str(p))
 for v in variants.values():
  for entry in v if isinstance(v,list) else [v]:
   model=entry['model'];path=A/'models'/(model.split(':')[1]+'.json')
   if not path.exists():errors.append('missing model '+str(path))
# Regression for actual client-1 failures: Jade config translations and Curios loader prefixes.
import re
jade=R/'src/main/java/org/goldgom/chemicalelements/compat/jade'
uids=set()
for source in jade.rglob('*.java'):
 uids.update(re.findall(r'fromNamespaceAndPath\("chemicalelements","(machine_[a-z_]+)"\)',source.read_text()))
for language in ('en_us','zh_cn'):
 translations=json.loads((A/'lang'/(language+'.json')).read_text(encoding='utf-8'))
 for uid in uids:
  key='config.jade.plugin_chemicalelements.'+uid
  if not translations.get(key):errors.append('missing Jade translation '+language+' '+key)
data=R/'src/main/resources/data'
entity=data/'chemicalelements/curios/entities/chemicalelements_player.json'
slot=data/'chemicalelements/curios/slots/charm.json'
if not entity.exists() or not slot.exists():errors.append('missing Curios loader-prefixed definition')
else:
 binding=json.loads(entity.read_text());definition=json.loads(slot.read_text())
 if 'minecraft:player' not in binding.get('entities',[]) or 'charm' not in binding.get('slots',[]):errors.append('missing Curios player charm binding')
 if definition.get('size',0)<1:errors.append('empty charm slot')
tag=json.loads((data/'curios/tags/item/charm.json').read_text())
for tier in ('basic','advanced','research'):
 if 'chemicalelements:detector_'+tier not in tag.get('values',[]):errors.append('missing wearable detector tag '+tier)
# Contact sheet is explicitly not runtime proof. Kept in build evidence, not shipped.
catalog=json.loads((E/'r16-art-inventory.json').read_text())['elements'];items=[A/'textures/item'/(e['id']+'.png') for e in catalog];W=18*36;H=7*36
pix=[bytearray([20,31,40,255]*W) for _ in range(H)]
for i,p in enumerate(items):
 w,h,rows=decode(p);x=(i%18)*36+2;y=(i//18)*36+2
 for yy,row in enumerate(rows):pix[y+yy][x*4:(x+w)*4]=bytes(row)
def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
(E/'r16-elements-preview-NOT-CLIENT.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+r for r in pix),9))+chunk(b'IEND',b''))
result={'textures':len(textures),'unique_texture_hashes':len(hashes),'model_json':len(list((A/'models').rglob('*.json'))),'errors':errors,'client_verified':False}
(E/'r16-static-verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result));assert not errors
