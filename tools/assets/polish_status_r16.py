from pathlib import Path
import json,struct,zlib,hashlib
R=Path(__file__).resolve().parents[2];A=R/'src/main/resources/assets/chemicalelements';M=R/'tools/assets/r16-art-manifest.json';manifest=json.loads(M.read_text());legacyPath=R/'tools/assets/manifest.json';legacy=json.loads(legacyPath.read_text())
def decode(path):
 b=path.read_bytes();w,h=struct.unpack('>II',b[16:24]);raw=b'';i=8
 while i<len(b):
  n=int.from_bytes(b[i:i+4],'big');t=b[i+4:i+8]
  if t==b'IDAT':raw+=b[i+8:i+8+n]
  i+=n+12
 raw=zlib.decompress(raw);stride=w*4+1;assert all(raw[y*stride]==0 for y in range(h))
 return w,h,[[tuple(raw[y*stride+1+x*4:y*stride+5+x*4]) for x in range(w)] for y in range(h)]
def encode(w,h,p):
 def ck(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\0'+bytes(v for px in row for v in px) for row in p)
 return b'\x89PNG\r\n\x1a\n'+ck(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+ck(b'IDAT',zlib.compress(raw,9))+ck(b'IEND',b'')
checks=0;names=[]
for model in sorted((A/'models/block').glob('*.json')):
 d=json.loads(model.read_text())
 if 'front' not in d.get('textures',{}) or not (A/'models/block'/f'{model.stem}_state_3.json').exists():continue
 name=model.stem;names.append(name)
 for state in (1,2,3):
  path=A/'textures/block'/f'{name}_state_{state}.png';rel=path.relative_to(R).as_posix();assert hashlib.sha256(path.read_bytes()).hexdigest()==manifest[rel]
  w,h,p=decode(path);assert (w,h)==(32,32)
  col={1:(54,226,206,255),2:(248,184,62,255),3:(255,102,77,255)}[state]
  def rect(x,y,u,v,c):
   for yy in range(y,v):
    for xx in range(x,u):p[yy][xx]=c
  # Wide main-face perimeter, separate from functional central machine artwork.
  for a,b,c,e in ((3,4,29,6),(3,24,29,26),(3,4,5,26),(27,4,29,26)):rect(a,b,c,e,col)
  # Large status plaque across lower face: run chevron, pause bars, missing X.
  rect(9,19,23,30,(14,25,33,255))
  if state==1:
   for y in range(21,28):rect(13,y,14+min(y-21,27-y),y+1,col)
  elif state==2:rect(12,21,15,28,col);rect(18,21,21,28,col)
  else:
   for i in range(8):rect(12+i,21+i,14+i,22+i,col);rect(19-i,21+i,21-i,22+i,col)
  data=encode(w,h,p);path.write_bytes(data);manifest[rel]=legacy[rel]=hashlib.sha256(data).hexdigest()
  stateModel=json.loads((A/'models/block'/f'{name}_state_{state}.json').read_text());assert stateModel['textures']['front']==f'chemicalelements:block/{name}_state_{state}'
  variants=json.loads((A/'blockstates'/f'{name}.json').read_text())['variants']
  for facing in ('north','east','south','west'):
   key=next(k for k in variants if f'facing={facing}' in k and f'visual={state}' in k);assert variants[key]['model']==f'chemicalelements:block/{name}_state_{state}';checks+=1
assert len(names)==25,names
M.write_text(json.dumps(manifest,indent=2)+'\n');legacyPath.write_text(json.dumps(legacy,indent=2)+'\n')
print(json.dumps({'machines':len(names),'state_textures':75,'four_direction_state_checks':checks,'colors_shapes':{'1':'cyan chevron','2':'amber pause bars','3':'coral X'},'runtime_state_modified':False}))
