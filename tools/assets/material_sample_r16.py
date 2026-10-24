from pathlib import Path
import json,struct,zlib,hashlib
R=Path(__file__).resolve().parents[2];A=R/'src/main/resources/assets/chemicalelements'
png=A/'textures/item/material_sample.png';model=A/'models/item/material_sample.json'
assert not png.exists() and not model.exists(),'refusing overwrite existing unknown sample assets'
W=32;p=[[(0,0,0,0) for x in range(W)] for y in range(W)]
def rect(x,y,u,v,c):
 for yy in range(y,v):
  for xx in range(x,u):p[yy][xx]=c
# Original sealed industrial specimen cassette: bevel chassis, glazed chamber,
# varied granular sample, numbered seal and asymmetrical locking lugs.
rect(6,3,26,29,(19,32,42,255));rect(4,7,28,25,(19,32,42,255))
rect(7,4,25,28,(87,115,128,255));rect(5,8,27,24,(87,115,128,255))
rect(8,5,24,7,(182,211,217,255));rect(7,7,9,25,(146,180,192,255))
rect(24,8,26,24,(43,65,78,255));rect(9,25,24,28,(43,65,78,255))
rect(10,8,23,24,(12,43,54,255));rect(11,9,22,23,(35,89,101,255))
for y in range(15,22):
 for x in range(12,21):
  if y>=18+(x%3)-1 or (x-16)**2+(y-19)**2<14:
   p[y][x]=[(212,168,91,255),(154,110,67,255),(239,205,132,255),(104,133,126,255)][(x*7+y*3)%4]
rect(12,10,14,16,(151,223,224,255));rect(14,10,18,11,(108,181,192,255))
rect(3,11,6,15,(211,155,68,255));rect(26,18,29,22,(211,155,68,255))
rect(11,25,21,27,(20,36,47,255))
for x in (12,15,18):rect(x,25,x+1,27,(104,224,204,255))
for x,y in ((7,8),(23,8),(7,23),(23,23)):rect(x,y,x+2,y+2,(210,228,228,255))
def ck(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
raw=b''.join(b'\0'+bytes(v for pixel in row for v in pixel) for row in p)
png.write_bytes(b'\x89PNG\r\n\x1a\n'+ck(b'IHDR',struct.pack('>IIBBBBB',W,W,8,6,0,0,0))+ck(b'IDAT',zlib.compress(raw,9))+ck(b'IEND',b''))
model.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'chemicalelements:item/material_sample'}},indent=2)+'\n')
for lang,name in [('zh_cn','物质样品'),('en_us','Material Sample')]:
 path=A/'lang'/f'{lang}.json';data=json.loads(path.read_text(encoding='utf-8'));key='item.chemicalelements.material_sample'
 assert key not in data or data[key]==name
 data[key]=name;path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for manifest in ('r16-art-manifest.json','manifest.json'):
 path=R/'tools/assets'/manifest;data=json.loads(path.read_text())
 for f in (png,model,A/'lang/zh_cn.json',A/'lang/en_us.json'):data[f.relative_to(R).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
 path.write_text(json.dumps(data,indent=2)+'\n')
print('Original 32x32 specimen cassette, dedicated item model and bilingual names created; no template matter assigned.')
