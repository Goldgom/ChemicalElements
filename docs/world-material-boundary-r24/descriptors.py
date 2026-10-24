import struct,zipfile,pathlib,json
R=pathlib.Path(r'E:/Projects/ChemicalElements');O=R/'docs/world-material-boundary-r24'
classes=['world/level/Level','world/level/chunk/LevelChunk','world/level/chunk/ChunkAccess','world/level/chunk/LevelChunkSection','world/level/chunk/PalettedContainer','world/level/chunk/storage/ChunkStorage','world/level/chunk/storage/ChunkSerializer','server/level/ChunkMap','world/level/Explosion','world/level/chunk/storage/RegionFile','world/level/chunk/storage/RegionFileStorage','network/protocol/game/ClientboundLevelChunkPacketData']
z=zipfile.ZipFile(R/'build/moddev/artifacts/neoforge-21.1.264-merged.jar')
def methods(b):
 pos=8
 def u(n):
  nonlocal pos
  x=int.from_bytes(b[pos:pos+n],'big');pos+=n;return x
 cp=[None]*u(2);i=1
 while i<len(cp):
  t=u(1)
  if t==1: n=u(2);cp[i]=b[pos:pos+n].decode('utf8',errors='replace');pos+=n
  elif t in (3,4):pos+=4
  elif t in (5,6):pos+=8;i+=1
  elif t in (7,8,16,19,20):pos+=2
  elif t in (9,10,11,12,17,18):pos+=4
  elif t==15:pos+=3
  else:raise ValueError(t)
  i+=1
 pos+=6;ninterfaces=u(2);pos+=2*ninterfaces
 def attrs():
  nonlocal pos
  for _ in range(u(2)):u(2);n=u(4);pos+=n
 for _ in range(u(2)):pos+=6;attrs()
 out=[]
 for _ in range(u(2)):
  flags=u(2);name=cp[u(2)];desc=cp[u(2)];attrs();out.append(name+desc)
 return out
out={c:methods(z.read('net/minecraft/'+c+'.class')) for c in classes}
(O/'descriptors.json').write_text(json.dumps(out,indent=2),encoding='utf8')
for c,ms in out.items():
 print('\n'+c)
 print('\n'.join(m for m in ms if not m.startswith('lambda$')))
