"""Original deterministic industrial chemistry art. No downloaded/vanilla pixels.
Only replaces assets proven by the original team manifest/generators or this generator.
Does not touch chemistry data, registries, recipes or user-owned unknown resources.
"""
from pathlib import Path
import hashlib,json,math,re,struct,zlib
R=Path(__file__).resolve().parents[2]; A=R/'src/main/resources/assets/chemicalelements'
E=R/'build/d-evidence'; E.mkdir(parents=True,exist_ok=True)
MAN=R/'tools/assets/manifest.json'; OLD=json.loads(MAN.read_text())
OWN=R/'tools/assets/r16-art-manifest.json'
PRIOR=json.loads(OWN.read_text()) if OWN.exists() else {}
changed={}; preserved=[]; before={}
def sha(b):return hashlib.sha256(b).hexdigest()
def put(p,b,known=False):
 p=Path(p); rel=p.relative_to(R).as_posix()
 if isinstance(b,dict):b=(json.dumps(b,ensure_ascii=False,indent=2)+'\n').encode()
 if p.exists():
  current=p.read_bytes(); h=sha(current);before[rel]=h
  if current==b:changed[rel]=h;return
  if not(known or OLD.get(rel)==h or PRIOR.get(rel)==h):preserved.append(rel);return
  # Immutable original bytes retained locally for provenance/recovery, never deleted.
  backup=E/'r16-before'/rel
  if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(current)
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b);changed[rel]=sha(b)
class Art:
 def __init__(self,w=32,h=32,bg=(0,0,0,0)):self.w=w;self.h=h;self.p=[[bg for _ in range(w)] for _ in range(h)]
 def rect(self,x,y,x2,y2,c):
  c=tuple(c)+(255,) if len(c)==3 else tuple(c)
  for yy in range(max(0,y),min(self.h,y2)):
   for xx in range(max(0,x),min(self.w,x2)):self.p[yy][xx]=c
 def line(self,x,y,x2,y2,c):
  n=max(abs(x2-x),abs(y2-y),1)
  for i in range(n+1):self.rect(round(x+(x2-x)*i/n),round(y+(y2-y)*i/n),round(x+(x2-x)*i/n)+1,round(y+(y2-y)*i/n)+1,c)
 def ellipse(self,x,y,rx,ry,c):
  for yy in range(y-ry,y+ry+1):
   for xx in range(x-rx,x+rx+1):
    if (xx-x)**2/(rx*rx)+(yy-y)**2/(ry*ry)<=1:self.rect(xx,yy,xx+1,yy+1,c)
 def text(self,s,x,y,c,scale=1):
  for ch in s.upper():
   bits=FONT.get(ch,FONT['?'])
   for yy,row in enumerate(bits.split('/')):
    for xx,v in enumerate(row):
     if v=='1':self.rect(x+xx*scale,y+yy*scale,x+(xx+1)*scale,y+(yy+1)*scale,c)
   x+=4*scale
 def png(self):
  def ck(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
  raw=b''.join(b'\0'+bytes(v for p in row for v in p) for row in self.p)
  return b'\x89PNG\r\n\x1a\n'+ck(b'IHDR',struct.pack('>IIBBBBB',self.w,self.h,8,6,0,0,0))+ck(b'IDAT',zlib.compress(raw,9))+ck(b'IEND',b'')
FONT=dict(zip('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ?',[
'111/101/101/101/111','010/110/010/010/111','110/001/111/100/111','110/001/011/001/110','101/101/111/001/001','111/100/110/001/110','011/100/111/101/111','111/001/010/010/010','111/101/111/101/111','111/101/111/001/110',
'010/101/111/101/101','110/101/110/101/110','011/100/100/100/011','110/101/101/101/110','111/100/110/100/111','111/100/110/100/100','011/100/101/101/011','101/101/111/101/101','111/010/010/010/111','001/001/001/101/010','101/101/110/101/101','100/100/100/100/111','101/111/111/101/101','101/111/111/111/101','010/101/101/101/010','110/101/110/100/100','010/101/101/111/011','110/101/110/101/101','011/100/010/001/110','111/010/010/010/010','101/101/101/101/111','101/101/101/101/010','101/101/111/111/101','101/101/010/101/101','101/101/010/010/010','111/001/010/100/111','110/001/010/000/010']))
STEEL=(57,75,86);DARK=(16,28,37);LIGHT=(186,207,211);TEAL=(60,198,182)
def mix(c,k):return tuple(max(0,min(255,int(v*k))) for v in c[:3])
def texture(name,art):put(A/'textures'/f'{name}.png',art.png())
def item(ident):put(A/'models/item'/f'{ident}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'chemicalelements:item/{ident}'}})
names=re.findall(r'"([a-z]+)"',(R/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text().split('NAMES=List.of(',1)[1].split(');',1)[0])
symbols='H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og'.split()
# Group/period positions explicitly follow the periodic table, f-block kept in own ordered bands.
positions=[]
for period,groups in [(1,[1,18]),(2,[1,2,13,14,15,16,17,18]),(3,[1,2,13,14,15,16,17,18]),(4,list(range(1,19))),(5,list(range(1,19)))]:
 positions.extend((period,g,'main') for g in groups)
for period in [6,7]:
 positions.extend((period,g,'main') for g in [1,2]);positions.extend((period,3,'lanthanide' if period==6 else 'actinide') for _ in range(15));positions.extend((period,g,'main') for g in range(4,19))
assert len(positions)==len(names)==len(symbols)==118
palette={'alkali':(210,135,95),'alkaline':(205,183,107),'transition':(91,161,180),'post':(118,169,152),'metalloid':(104,185,122),'nonmetal':(105,175,203),'halogen':(178,159,213),'noble':(203,139,180),'lanthanide':(158,169,106),'actinide':(192,133,100)}
nonmetal={1,6,7,8,15,16,34};metalloid={5,14,32,33,51,52};gas={1,2,7,8,9,10,17,18,36,54,86,118};liquid={35,80}
catalog=[]
for z,(name,symbol,(period,group,series)) in enumerate(zip(names,symbols,positions),1):
 family=series if series!='main' else 'noble' if group==18 else 'halogen' if group==17 else 'nonmetal' if z in nonmetal else 'metalloid' if z in metalloid else 'alkali' if group==1 else 'alkaline' if group==2 else 'transition' if group<=12 else 'post'
 c=mix(palette[family],0.84+period*.025); phase='gas' if z in gas else 'liquid' if z in liquid else 'nonmetal' if z in nonmetal|metalloid or group==17 else 'metal'
 a=Art();a.rect(4,3,28,30,DARK);a.rect(5,4,27,29,STEEL);a.rect(6,5,26,28,mix(c,.52));a.rect(6,5,26,7,c)
 # A sealed ampoule for gas/liquid; cut specimen/crystal for solid samples.
 if phase in ('gas','liquid'):
  a.rect(11,8,21,10,LIGHT);a.rect(9,10,23,24,(101,146,159));a.rect(10,11,22,23,(27,49,61));a.rect(10,17 if phase=='liquid' else 12,22,23,mix(c,.9));a.line(10,11,10,21,(198,228,230))
  if phase=='gas':
   for bx,by in [(13,14),(18,17),(14,21)]:a.ellipse(bx,by,1,1,LIGHT)
  else:a.line(11,17,20,17,LIGHT);a.ellipse(18,20,1,1,mix(c,1.3))
 elif phase=='metal':
  for yy in range(10,23):
   inset=abs(16-yy)//4;a.rect(9+inset,yy,24-inset,yy+1,mix(c,1.2-(yy-10)*.035));a.line(10,11,20,11,LIGHT);a.line(9,12,9,21,mix(c,1.4))
 else:
  for yy in range(9,24):
   half=min(yy-8,24-yy,7);a.rect(16-half,yy,17+half,yy+1,mix(c,1.1 if yy%3 else .8))
  a.line(16,9,13,20,LIGHT);a.line(16,9,21,19,mix(c,.65))
 # Top-left symbol, bottom serial and group/period not arbitrary random coloration.
 a.rect(6,7,6+len(symbol)*4,13,DARK);a.text(symbol,6,7,LIGHT);a.rect(6,24,26,28,DARK);a.text(str(z),7,24,c)
 for p in range(period):a.rect(27,5+p*3,28,7+p*3,c)
 texture('item/element_'+name,a);item('element_'+name);catalog.append({'id':'element_'+name,'z':z,'symbol':symbol,'period':period,'group':group,'family':family,'appearance':phase})
# Dedicated schematic glyphs, drawn from functional silhouettes rather than reused artwork.
def glyph(a,i,c):
 cx=16;cy=16
 kind=i%8
 if kind==0:
  a.ellipse(cx,cy,7,7,c);a.ellipse(cx,cy,4,4,DARK);a.line(16,9,16,23,LIGHT);a.line(9,16,23,16,LIGHT)
 elif kind==1:
  a.rect(11,9,21,23,c);a.rect(13,11,19,21,DARK)
  for y in (12,16,20):a.line(9,y,23,y,LIGHT)
 elif kind==2:
  for x in range(9,23,3):a.line(x,10,x,22,c)
  a.line(8,10,24,10,LIGHT);a.line(8,22,24,22,LIGHT)
 elif kind==3:
  a.line(10,10,22,22,c);a.line(22,10,10,22,c);a.ellipse(16,16,3,3,LIGHT)
 elif kind==4:
  a.rect(9,10,23,22,c);a.rect(11,12,21,20,DARK);a.line(11,18,20,13,LIGHT)
 elif kind==5:
  for y in (10,14,18,22):a.line(10,y,21,y,c)
  a.line(10,10,10,22,LIGHT);a.line(21,10,21,22,LIGHT)
 elif kind==6:
  a.ellipse(16,16,6,8,c);a.rect(11,16,22,24,DARK);a.line(10,16,22,16,LIGHT)
 else:
  for x,y in [(10,12),(22,12),(16,22)]:a.ellipse(x,y,3,3,c);a.line(x,y,16,16,LIGHT)
 a.text(str(i+1),3,25,LIGHT)
def panel(i,face,c):
 a=Art(bg=STEEL)
 for y in range(32):
  for x in range(32):
   v=1+(((x*13+y*7+i*5)%11)-5)*.009;a.rect(x,y,x+1,y+1,mix(STEEL,v))
 a.rect(0,0,32,2,DARK);a.rect(0,30,32,32,DARK);a.rect(0,0,2,32,DARK);a.rect(30,0,32,32,DARK)
 for x,y in [(4,4),(27,4),(4,27),(27,27)]:a.rect(x,y,x+2,y+2,LIGHT);a.rect(x,y,x+1,y+1,DARK)
 if face=='front':a.rect(7,7,25,25,DARK);glyph(a,i,c);a.rect(5,28,10,30,c);a.rect(21,28,27,30,(45,66,72))
 elif face=='side':
  for y in range(8,25,4):a.rect(8,y,24,y+2,DARK);a.rect(9,y+2,23,y+3,mix(STEEL,1.4))
  a.rect(3,12,6,20,c);a.text(str(i+1),18,25,LIGHT)
 else:
  a.rect(6,6,26,26,DARK);a.rect(8,8,24,24,mix(STEEL,1.4));a.ellipse(16,16,5,5,c);a.ellipse(16,16,3,3,DARK);a.line(16,9,16,22,LIGHT);a.text(str(i+1),3,25,c)
 return a
def box(fr,to,tex):
 # Each cuboid face gets planar UV in block coordinates, no stretched whole-face default.
 faces={}
 for d in ['north','south','east','west','up','down']:
  u0,v0,u1,v1=(fr[0],16-to[1],to[0],16-fr[1]) if d in ('north','south') else (fr[2],16-to[1],to[2],16-fr[1]) if d in ('east','west') else (fr[0],fr[2],to[0],to[2])
  faces[d]={'uv':[u0,v0,u1,v1],'texture':'#'+('front' if d=='north' else 'top' if d in ('up','down') else 'side')}
 return {'from':fr,'to':to,'faces':faces}
def model(ident,shapes,known=False):
 put(A/'models/block'/f'{ident}.json',{'textures':{k:f'chemicalelements:block/{ident}_{k}' for k in ('front','side','top')}|{'particle':f'chemicalelements:block/{ident}_side'},'elements':[box(a,b,None) for a,b in shapes]},known)
 put(A/'models/item'/f'{ident}.json',{'parent':f'chemicalelements:block/{ident}'},known)
machines=re.findall(r'^ [A-Z_]+\("([a-z_]+)"',(R/'src/main/java/org/goldgom/chemicalelements/machine/MachineKind.java').read_text(),re.M)
for i,name in enumerate(machines):
 c=list(palette.values())[i%10]
 for face in ('side','front','top'):texture('block/'+name+'_'+face,panel(i,face,c))
 shapes=[([1,1,1],[15,15,15]),([0,0,0],[16,2,16]),([0,14,0],[16,16,16]),([2,3,0],[14,13,1]),([0,5,5],[1,11,11]),([15,5,5],[16,11,11])]
 if name in ('storage_tank','gas_collector','dissolver','condenser'):shapes=[([3,2,3],[13,14,13]),([1,0,1],[15,3,15]),([1,13,1],[15,16,15]),([6,4,1],[10,12,3]),([0,6,6],[3,10,10]),([13,6,6],[16,10,10])]
 if name=='pipe':shapes=[([5,5,0],[11,11,16]),([3,3,0],[13,13,2]),([3,3,14],[13,13,16])]
 model(name,shapes,known=True)
 variants={}
 for state,color in enumerate([(70,93,100),(78,222,164),(230,185,82),(231,104,96)]):
  front=panel(i,'front',c);front.rect(21,28,27,30,color)
  # Shape as well as color communicates state, no animation claiming nonexistent outputs.
  if state==1:front.line(23,28,25,29,LIGHT)
  elif state==2:front.rect(22,27,23,30,LIGHT);front.rect(25,27,26,30,LIGHT)
  elif state==3:front.line(21,26,27,30,LIGHT);front.line(27,26,21,30,LIGHT)
  texture(f'block/{name}_state_{state}',front)
  put(A/'models/block'/f'{name}_state_{state}.json',{'parent':f'chemicalelements:block/{name}','textures':{'front':f'chemicalelements:block/{name}_state_{state}'}})
  for d,rot in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={d},visual={state}']={'model':f'chemicalelements:block/{name}_state_{state}','y':rot,'uvlock':True}
 put(A/'blockstates'/f'{name}.json',{'variants':variants},known=True)
# Per-cell models reveal coherent large-scale structural bands and functional bays.
parts={'port':(1,1,1),'column_short':(1,2,1),'column_tall':(1,3,1),'core':(2,2,2),'assembly':(3,3,3),'tower':(3,6,3)}
for i,(name,size) in enumerate(parts.items()):
 ident='part_'+name;c=list(palette.values())[i+1]
 for face in ('front','side','top'):texture('block/'+ident+'_'+face,panel(i+25,face,c))
 variants={}
 for x in range(size[0]):
  for y in range(size[1]):
   for z in range(size[2]):
    cell=f'{ident}_{x}_{y}_{z}';shapes=[([1,0,1],[15,16,15])]
    if x==0:shapes.append(([0,0,0],[2,16,16]))
    if x==size[0]-1:shapes.append(([14,0,0],[16,16,16]))
    if y==0:shapes.append(([0,0,0],[16,2,16]))
    if y==size[1]-1 or (name=='tower' and y in (2,4)):shapes.append(([0,14,0],[16,16,16]))
    if z==0:shapes.append(([3,4,0],[13,12,1]))
    if name=='port':shapes=[([2,2,2],[14,14,14]),([4,4,0],[12,12,3]),([5,5,0],[11,11,1])]
    put(A/'models/block'/f'{cell}.json',{'textures':{k:f'chemicalelements:block/{ident}_{k}' for k in ('front','side','top')}|{'particle':f'chemicalelements:block/{ident}_side'},'elements':[box(a,b,None) for a,b in shapes]})
    for d,rot in [('north',0),('east',90),('south',180),('west',270)]:variants[f'part_x={x},part_y={y},part_z={z},facing={d}']={'model':f'chemicalelements:block/{cell}','y':rot,'uvlock':True}
 # All syntactically allowed states need a model, including invalid coordinates after data reload.
 for x in range(3):
  for y in range(6):
   for z in range(3):
    for d,rot in [('north',0),('east',90),('south',180),('west',270)]:variants.setdefault(f'part_x={x},part_y={y},part_z={z},facing={d}',{'model':f'chemicalelements:block/{ident}_0_0_0','y':rot,'uvlock':True})
 put(A/'blockstates'/f'{ident}.json',{'variants':variants},known=True)
 put(A/'models/block'/f'{ident}.json',{'parent':f'chemicalelements:block/{ident}_0_0_0'},known=True)
 put(A/'models/item'/f'{ident}.json',{'parent':f'chemicalelements:block/{ident}_0_0_0'},known=True)
for i,tier in enumerate(['basic','advanced','research']):
 a=Art();c=[(103,185,168),(106,171,211),(199,158,216)][i];a.rect(7,4,25,28,DARK);a.rect(8,5,24,27,STEEL);a.rect(10,7,22,20,c);a.rect(11,8,21,19,(20,44,54));a.line(12,16,15,11,c);a.line(15,11,18,16,c);a.line(18,16,20,10,c);a.rect(11,22,14,25,LIGHT);a.rect(17,22,21,24,c)
 for j in range(i+1):a.rect(10+j*4,2,12+j*4,5,c)
 a.text(str(i+1),18,12,LIGHT);texture('item/detector_'+tier,a);put(A/'models/item'/f'detector_{tier}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'chemicalelements:item/detector_{tier}'}},known=True)
# These exact template references were created by complete_category_assets.py, not user art.
for ident,reference in [('example_item','minecraft:item/apple'),('example_block','minecraft:block/stone')]:
 path=A/('models/item' if ident=='example_item' else 'models/block')/f'{ident}.json';j=json.loads(path.read_text())
 expected={'parent':'minecraft:item/generated','textures':{'layer0':reference}} if ident=='example_item' else {'parent':'minecraft:block/cube_all','textures':{'all':reference}}
 if j==expected or path.relative_to(R).as_posix() in PRIOR:
  a=panel(50 if ident=='example_block' else 51,'front',TEAL);texture(('block/' if ident=='example_block' else 'item/')+ident,a)
  put(path,{'parent':'minecraft:block/cube_all','textures':{'all':'chemicalelements:block/example_block'}} if ident=='example_block' else {'parent':'minecraft:item/generated','textures':{'layer0':'chemicalelements:item/example_item'}},known=True)
# Non-element species are currently data-only, retain separate asset IDs for future public registry integration.
for i,p in enumerate(sorted((R/'src/main/resources/data/chemicalelements/chemicalelements/species').glob('*.json'))):
 if p.stem in names:continue
 j=json.loads(p.read_text());a=Art();c=list(palette.values())[i%10];a.rect(7,4,25,29,DARK);a.rect(9,8,23,27,mix(c,.5));a.rect(11,3,21,8,LIGHT);glyph(a,i,c);texture('item/species_'+p.stem,a);item('species_'+p.stem)
for kind,w,h in [('machine_panel',256,256),('detector_panel',256,256),('periodic_panel',256,256),('slot',18,18),('tank',24,64),('jei_panel',160,84),('jade_panel',128,48)]:
 a=Art(w,h,(20,31,40,255));a.rect(1,1,w-1,h-1,STEEL);a.rect(3,3,w-3,h-3,(25,40,50));a.rect(4,4,w-4,6,TEAL)
 if kind=='slot':a.rect(1,1,17,17,DARK);a.rect(2,2,16,16,(43,58,65));a.line(2,16,16,16,LIGHT);a.line(2,2,15,2,(13,23,29));a.rect(3,3,15,4,(34,47,54))
 elif kind=='tank':
  a.rect(5,8,19,59,DARK)
  for y in range(11,59,6):a.rect(2,y,5,y+1,LIGHT)
 elif kind.endswith('panel'):
  for x in range(8,w-8,18):a.rect(x,h-8,x+8,h-7,(58,87,98))
  if kind=='machine_panel':
   a.rect(169,15,172,230,(49,74,86));a.rect(178,27,235,65,DARK)
   for y in range(31,62,6):a.rect(183,y,230,y+1,(48,91,101))
  elif kind=='detector_panel':
   a.rect(7,36,249,38,(51,96,108));a.rect(7,41,9,230,TEAL)
  elif kind=='periodic_panel':
   for x in range(12,240,13):
    for y in range(28,220,22):a.rect(x,y,x+10,y+18,(35,58,69))
  elif kind=='jei_panel':a.rect(105,12,108,71,TEAL);a.line(110,42,145,42,LIGHT)
  elif kind=='jade_panel':a.rect(7,13,28,37,DARK);a.rect(34,16,112,18,(72,108,118))
 texture('gui/'+kind,a)
# Persistent inventory and ownership manifests merge rather than replace legacy hashes.
report={'version':'r16-art-1','original_process':'stdlib raster, original mathematical geometry, no external pixels','before':before,'managed':changed,'preserved_unknown':preserved,'elements':catalog,'registered_scope':{'elements':118,'machines':len(machines),'parts':6,'detectors':3},'data_only_species':8,'client_verified':False}
put(E/'r16-art-inventory.json',report,known=True)
merged=dict(PRIOR);merged.update(changed);OWN.write_text(json.dumps(merged,indent=2)+'\n')
legacy=dict(OLD);legacy.update(changed);MAN.write_text(json.dumps(legacy,indent=2)+'\n')
print(json.dumps({'managed':len(changed),'preserved_unknown':preserved,'version':'r16-art-1','client_verified':False}))
