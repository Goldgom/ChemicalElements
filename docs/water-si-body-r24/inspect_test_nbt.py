"""Read only: fixed compat-r17 test world, max 4 region files / 32 chunks each. No writes to saves."""
import gzip, zlib, struct, json, hashlib
from pathlib import Path
ROOT=Path('E:/Projects/ChemicalElements')
WORLD=ROOT/'run/compat-r17/saves/新的世界'
class NBT:
 def __init__(self,b): self.b=b; self.i=0
 def take(self,n):
  if n<0 or self.i+n>len(self.b): raise ValueError('bounds')
  v=self.b[self.i:self.i+n];self.i+=n;return v
 def num(self,f): return struct.unpack('>'+f,self.take(struct.calcsize('>'+f)))[0]
 def text(self): return self.take(self.num('H')).decode('utf8')
 def val(self,t,depth=0):
  if depth>64: raise ValueError('depth')
  if t in range(1,7): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7: return {'byte_array_length':len(self.take(self.num('i')))}
  if t==8: return self.text()
  if t==9:
   sub=self.num('B');n=self.num('i')
   if not 0<=n<=1000000: raise ValueError('list budget')
   return [self.val(sub,depth+1) for _ in range(n)]
  if t==10:
   out={}
   while True:
    sub=self.num('B')
    if sub==0: return out
    name=self.text();out[name]=self.val(sub,depth+1)
  if t in (11,12):
   n=self.num('i');self.take(n*(4 if t==11 else 8));return {'array_length':n}
  raise ValueError('tag '+str(t))
 def root(self):
  t=self.num('B');self.text();return self.val(t)
def find_water(x,path=''):
 found=[]
 if isinstance(x,dict):
  if 'schema_version' in x and 'water' in x: found.append({'path':path,'schema_version':x['schema_version'],'water_count':len(x['water']),'sample':x['water'][:2]})
  for k,v in x.items(): found+=find_water(v,path+'/'+k)
 elif isinstance(x,list):
  for i,v in enumerate(x):
   if isinstance(v,(dict,list)):found+=find_water(v,path+'/'+str(i))
 return found
level_path=WORLD/'level.dat'; level=NBT(gzip.decompress(level_path.read_bytes())).root()
report={'scope':str(WORLD),'level_sha256':hashlib.sha256(level_path.read_bytes()).hexdigest(),'DataVersion':level.get('Data',{}).get('DataVersion'),'Version':level.get('Data',{}).get('Version'),'regions':[],'actual_water_samples':[]}
for path in sorted((WORLD/'region').glob('*.mca'))[:4]:
 raw=path.read_bytes(); item={'path':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(raw).hexdigest(),'chunks_read':0,'data_versions':[],'errors':[]}
 for i in range(1024):
  off=int.from_bytes(raw[i*4:i*4+3],'big')*4096
  if not off:continue
  if item['chunks_read']>=32:break
  try:
   n=int.from_bytes(raw[off:off+4],'big');mode=raw[off+4];b=raw[off+5:off+4+n]
   if mode==2:b=zlib.decompress(b)
   elif mode==1:b=gzip.decompress(b)
   elif mode!=3:raise ValueError('unsupported compression/external '+str(mode))
   if len(b)>16000000:raise ValueError('chunk size')
   chunk=NBT(b).root();item['chunks_read']+=1;item['data_versions'].append(chunk.get('DataVersion'))
   for sample in find_water(chunk):report['actual_water_samples'].append({'region':path.name,'slot':i,**sample})
  except Exception as e:item['errors'].append({'slot':i,'error':str(e)})
 item['data_versions']=sorted(set(item['data_versions']));report['regions'].append(item)
report['conclusion']='Actual chemistry water producer/schema unknown: no water attachment found in bounded real sample' if not report['actual_water_samples'] else 'Actual chemistry water attachment found; inspect versions before migration'
print(json.dumps(report,ensure_ascii=False,indent=2))
