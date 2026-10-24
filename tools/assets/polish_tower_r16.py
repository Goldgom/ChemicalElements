from pathlib import Path
import json,hashlib
import struct,zlib
class Image:
 @staticmethod
 def new(mode,size,col):return Raster(size,col)
class ImageDraw:
 @staticmethod
 def Draw(im):return im
class Raster:
 def __init__(self,size,col):self.w,self.h=size;self.p=[[col for x in range(self.w)] for y in range(self.h)]
 def point(self,xy,fill):
  x,y=xy
  if 0<=x<self.w and 0<=y<self.h:self.p[y][x]=fill
 def rectangle(self,rect,fill):
  a,b,c,d=rect
  for y in range(b,d+1):
   for x in range(a,c+1):self.point((x,y),fill)
 def line(self,coords,fill,width=1):
  for i in range(0,len(coords)-2,2):
   x,y,u,v=coords[i:i+4];steps=max(abs(u-x),abs(v-y),1)
   for t in range(steps+1):
    for k in range(width):self.point((round(x+(u-x)*t/steps)+k,round(y+(v-y)*t/steps)),fill)
 def save(self,path):
  def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
  raw=b''.join(b'\0'+bytes(c for pixel in row for c in (*pixel,255)) for row in self.p)
  path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
R=Path(__file__).resolve().parents[2]; A=R/'src/main/resources/assets/chemicalelements'
# Global 48x96x48 geometry, sliced into the existing 54 cells. No world/state changes.
shapes=[]
def box(a,b,mat):shapes.append((a,b,mat))
box([0,0,0],[48,7,48],'frame');box([2,7,2],[46,15,46],'base')
box([5,15,5],[43,79,43],'shell')
for y in (15,46,76):box([1,y,1],[47,y+5,47],'frame')
for x in (1,43):
 for z in (1,43):box([x,7,z],[x+4,87,z+4],'frame')
# Broad recessed process windows: continuous vertical columns, not repeated cabinet tiles.
for z in (3,43):
 for x in (10,27):box([x,22,z],[x+11,44,z+2],'glass');box([x,54,z],[x+11,74,z+2],'glass')
for x in (3,43):
 for z in (10,27):box([x,22,z],[x+2,44,z+11],'glass');box([x,54,z],[x+2,74,z+11],'glass')
# Crown, three tall manifold risers and inset service panel.
box([3,81,3],[45,87,45],'base')
for x in (7,21,35):box([x,87,12],[x+6,96,36],'pipe')
box([17,7,0],[31,15,3],'control')
colors={'frame':(111,144,156),'base':(53,76,91),'shell':(67,96,108),'glass':(30,103,124),'pipe':(178,151,98),'control':(43,81,90)}
for i,(name,col) in enumerate(colors.items()):
 im=Image.new('RGB',(32,32),col);d=ImageDraw.Draw(im)
 for y in range(32):
  for x in range(32):
   v=((x*17+y*11+i*7)%7)-3;d.point((x,y),fill=tuple(max(0,min(255,c+v)) for c in col))
 if name=='glass':
  d.rectangle((2,0,4,31),fill=(105,202,209));d.line((8,0,24,31),fill=(47,137,153),width=2)
 elif name=='control':
  d.rectangle((4,5,26,21),fill=(13,34,42));d.line((7,17,12,10,18,15,23,7),fill=(119,224,201),width=2)
 elif name=='frame':
  d.line((0,2,31,2),fill=(178,196,196),width=2);d.line((0,28,31,28),fill=(59,84,97),width=2)
 elif name=='pipe':
  for x in (4,26):d.line((x,0,x,31),fill=(213,192,143),width=2)
 else:d.line((0,0,31,0),fill=tuple(min(255,c+25) for c in col))
 im.save(A/'textures/block'/f'tower12_{name}.png')
textures={k:f'chemicalelements:block/tower12_{k}' for k in colors};textures['particle']=textures['shell']
def faces():return {direction:{'texture':'#'+mat,'uv':[0,0,16,16]} for direction in ('north','south','east','west','up','down')}
for x in range(3):
 for y in range(6):
  for z in range(3):
   lo=[x*16,y*16,z*16];hi=[v+16 for v in lo];elements=[]
   for a,b,mat in shapes:
    fr=[max(a[j],lo[j]) for j in range(3)];to=[min(b[j],hi[j]) for j in range(3)]
    if any(fr[j]>=to[j] for j in range(3)):continue
    elements.append({'from':[fr[j]-lo[j] for j in range(3)],'to':[to[j]-lo[j] for j in range(3)],'faces':faces()})
   (A/'models/block'/f'part_tower_{x}_{y}_{z}.json').write_text(json.dumps({'textures':textures,'elements':elements},indent=2)+'\n')
# Item uses the same global assembly, uniformly normalized to vanilla item bounds.
item=[]
for a,b,mat in shapes:item.append({'from':[4+a[0]/6,a[1]/6,4+a[2]/6],'to':[4+b[0]/6,b[1]/6,4+b[2]/6],'faces':faces()})
(A/'models/item/part_tower.json').write_text(json.dumps({'textures':textures,'elements':item,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.8,.8,.8]}}},indent=2)+'\n')
for manifest in (R/'tools/assets/r16-art-manifest.json',R/'tools/assets/manifest.json'):
 m=json.loads(manifest.read_text())
 for p in list((A/'models/block').glob('part_tower_*_*.json'))+[A/'models/item/part_tower.json']+list((A/'textures/block').glob('tower12_*.png')):m[p.relative_to(R).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
 manifest.write_text(json.dumps(m,indent=2)+'\n')
print('54 world tower cells + matching whole item, 6 original materials; dimensions/states/placement untouched')
