"""Deterministic original pixel art, no external image dependencies. Run from repository root."""
from pathlib import Path
import json, re, zlib, struct, hashlib
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/chemicalelements'
DATA=ROOT/'src/main/resources/data/chemicalelements'
def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def png(path,pixels):
    path.parent.mkdir(parents=True,exist_ok=True)
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    raw=b''.join(b'\0'+bytes(v for pixel in row for v in pixel) for row in pixels)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
names=re.findall(r'"([a-z]+)"',(ROOT/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text().split('NAMES=List.of(',1)[1].split(');',1)[0])
machines=re.findall(r'^ [A-Z_]+\("([a-z_]+)"', (ROOT/'src/main/java/org/goldgom/chemicalelements/machine/MachineKind.java').read_text(),re.M)
assert len(names)==118 and len(machines)>=25
symbols='H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og'.split()
zh='氢 氦 锂 铍 硼 碳 氮 氧 氟 氖 钠 镁 铝 硅 磷 硫 氯 氩 钾 钙 钪 钛 钒 铬 锰 铁 钴 镍 铜 锌 镓 锗 砷 硒 溴 氪 铷 锶 钇 锆 铌 钼 锝 钌 铑 钯 银 镉 铟 锡 锑 碲 碘 氙 铯 钡 镧 铈 镨 钕 钷 钐 铕 钆 铽 镝 钬 铒 铥 镱 镥 铪 钽 钨 铼 锇 铱 铂 金 汞 铊 铅 铋 钋 砹 氡 钫 镭 锕 钍 镤 铀 镎 钚 镅 锔 锫 锎 锿 镄 钔 锘 铹 𬬻 𬭊 𬭳 𬭛 𬭶 鿏 𫟼 𬬭 鿔 鿭 𫓧 镆 𫟷 鿬 鿫'.split()
en={};cn={}
for i,name in enumerate(names,1):
    ident='element_'+name
    color=((53+i*37)%160+60,(89+i*53)%160+60,(31+i*19)%160+60,255)
    pixels=[]
    for y in range(16):
        row=[]
        for x in range(16):
            p=(0,0,0,0)
            if 3<=x<=12 and 3<=y<=14:p=(35,44,51,255) if x in (3,12) or y==14 else color
            if 5<=x<=10 and y in (1,2,3):p=(185,201,211,255)
            if 5<=x<=10 and 6<=y<=11:p=(230,239,236,255)
            # Binary atomic number label makes every original 16px tile distinct.
            if 5<=x<=11 and y in (7,9):p=color if i&(1<<(x-5)) else (31,47,55,255)
            row.append(p)
        pixels.append(row)
    png(ASSETS/'textures/item'/f'{ident}.png',pixels)
    write(ASSETS/'models/item'/f'{ident}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'chemicalelements:item/{ident}'}})
    en[f'item.chemicalelements.{ident}']=f'{name.title()} ({symbols[i-1]})'
    cn[f'item.chemicalelements.{ident}']=f'{zh[i-1]}（{symbols[i-1]}）'
zhmachines='分析台 取样台 研磨机 溶解槽 过滤器 离心机 蒸发器 结晶器 蒸馏器 加压反应釜 电解槽 换热器 冷凝器 气体收集器 储罐 泵 管道 吸附干燥床 真空室 高温炉 分馏塔 研究控制器 同位素分离机 屏蔽反应堆 常温合成台'.split()
for i,name in enumerate(machines):
    accent=((40+i*41)%180+50,(70+i*23)%170+60,(110+i*17)%140+80,255)
    for face in ('side','front','top'):
        pixels=[]
        for y in range(16):
            row=[]
            for x in range(16):
                p=(65,79,89,255) if (x+y)%2 else (71,86,96,255)
                if x in (0,15) or y in (0,15):p=(24,35,44,255)
                if x in (2,13) and y in (2,13):p=(172,187,190,255)
                if face=='front' and 4<=x<=11 and 4<=y<=11:p=accent if (x+y+i)%4 else (13,29,35,255)
                if face=='top' and 5<=x<=10 and 5<=y<=10:p=accent
                row.append(p)
            pixels.append(row)
        png(ASSETS/'textures/block'/f'{name}_{face}.png',pixels)
    write(ASSETS/'models/block'/f'{name}.json',{'parent':'minecraft:block/orientable','textures':{'top':f'chemicalelements:block/{name}_top','front':f'chemicalelements:block/{name}_front','side':f'chemicalelements:block/{name}_side'}})
    write(ASSETS/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'chemicalelements:block/{name}'}}})
    write(ASSETS/'models/item'/f'{name}.json',{'parent':f'chemicalelements:block/{name}'})
    write(DATA/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'chemicalelements:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    en[f'block.chemicalelements.{name}']=name.replace('_',' ').title();cn[f'block.chemicalelements.{name}']=zhmachines[i]
    # Shell recipes use vanilla materials; chemistry rewrite is validated by A's conservative recipe audit.
    # No ordinary crafting recipe claims to perform chemistry.
    write(DATA/'tags/block/mineable/pickaxe'/f'{name}.json',{'replace':False,'values':[f'chemicalelements:{name}']})
write(ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':[f'chemicalelements:{m}' for m in machines]})
for controller,size in [('fractionation_tower',[3,5,3]),('electrolyzer',[3,2,3]),('high_temperature_furnace',[3,3,3]),('reactor',[3,3,3]),('storage_tank',[3,4,3]),('research_controller',[5,3,5]),('shielded_reactor',[5,5,5])]:
    sx,sy,sz=size;parts=[]
    for y in range(sy):
        for x in range(sx):
            for z in range(sz):
                if x in (0,sx-1) or y in (0,sy-1) or z in (0,sz-1):
                    parts.append({'offset':[x-sx//2,y,z],'block':'minecraft:iron_block'})
    origin=[0,0,0]
    parts=[p for p in parts if p['offset']!=origin]
    write(DATA/'chemicalelements/multiblocks'/f'{controller}.json',{'schema_version':1,'controller':f'chemicalelements:{controller}','size':size,'parts':parts,'ports':[{'offset':[0,1,sz-1],'role':'material'},{'offset':[sx//2,1,sz//2],'role':'energy'}],'unloaded':'pause'})
en.update({'gui.chemicalelements.start':'Start','gui.chemicalelements.cancel':'Cancel','gui.chemicalelements.periodic_table':'Periodic table','gui.chemicalelements.unsynchronized':'Server composition unavailable','gui.chemicalelements.refresh':'Refresh','itemGroup.chemicalelements':'Chemical Elements'})
cn.update({'gui.chemicalelements.start':'开始','gui.chemicalelements.cancel':'取消','gui.chemicalelements.periodic_table':'元素周期表','gui.chemicalelements.unsynchronized':'服务器未提供组成','gui.chemicalelements.refresh':'刷新','itemGroup.chemicalelements':'化学元素'})
for lang,values in [('en_us',en),('zh_cn',cn)]:
    path=ASSETS/'lang'/f'{lang}.json'
    old=json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
    old.update(values);write(path,old)
files=sorted(list(ASSETS.rglob('*'))+list(DATA.rglob('*')))
manifest={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}
write(ROOT/'tools/assets/manifest.json',manifest)
print(f'Generated {len(names)} elements, {len(machines)} machines; {len(manifest)} resources hashed')
