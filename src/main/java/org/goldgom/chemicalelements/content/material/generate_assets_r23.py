"""Offline original pixel art and finite content assets. No vanilla texture inputs.
Run once; exclusive creation, refuse collisions. Geometry is a GAME carrier convention,
not density/pressure/phase evidence. Does not write any other datagen or build output.
"""
from pathlib import Path
import json, re, hashlib, struct, zlib, colorsys
ROOT = Path(__file__).resolve().parents[8]
assert ROOT == Path('E:/Projects/ChemicalElements')
R = ROOT/'src/main/resources'
A = R/'assets/chemicalelements'
D = R/'data/chemicalelements'
source=(ROOT/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text()
names=re.findall(r'"([a-z]+)"', source.split('NAMES=List.of(')[1].split(');')[0])
assert len(names)==118 and len(set(names))==118
forms=['solid','liquid','gas','unresolved']
ids=['elemental_'+n for n in names]+['mixture_carrier_'+f for f in forms]
out={}
def put(p, value):
    assert p not in out
    out[p]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode() if isinstance(value,dict) else value

def png(pixels):
    def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for rgb in row for c in rgb) for row in pixels)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

def texture(index, form, top):
    # Brushed inset tiles with bevel, corner fasteners and an 8-bit atomic-number seal.
    # Hue is an index coding convention, deliberately not a measured element appearance.
    hue=(index*0.61803398875)%1
    base=colorsys.hsv_to_rgb(hue,0.28,0.68)
    rows=[]
    for y in range(32):
        row=[]
        for x in range(32):
            noise=((x*13+y*7+index*11)%23-11)*2
            rgb=[int(v*255)+noise for v in base]
            if min(x,y,31-x,31-y)<3: rgb=[40+((x+y)%3)*9,48+((x+y)%3)*9,60+((x+y)%3)*9]
            elif x==3 or y==3: rgb=[220,228,235]
            elif x==28 or y==28: rgb=[44,48,55]
            if x in (5,26) and y in (5,26): rgb=[228,233,239]
            if 8<=x<=23 and 9<=y<=23:
                if form=='liquid': rgb=[45+(x%4)*5,110+(y%5)*8,170+(y%3)*9] if y>=15+(x//3)%3 else [100,120,130]
                elif form=='gas':
                    if ((x-12)**2+(y-14)**2<10 or (x-20)**2+(y-20)**2<13): rgb=[203,220,235]
                    else: rgb=[83,103,125]
                elif form=='unresolved': rgb=[170,110,42] if (x+y)//4%2 else [45,49,59]
                elif form=='solid': rgb=[int(v*220)+(x+y)%7*3 for v in base]
            if top and 8<=x<24 and 12<=y<20:
                bit=(x-8)//2
                rgb=[222,227,230] if (index>>(7-bit))&1 else [31,39,48]
            row.append(tuple(max(0,min(255,c)) for c in rgb))
        rows.append(row)
    assert len(set(c for row in rows for c in row))>20
    return png(rows)

def box(lo,hi,tex):
    return {'from':lo,'to':hi,'faces':{f:{'texture':tex} for f in ['north','south','east','west','up','down']}}
for i,path in enumerate(ids,1):
    form='candidate' if i<=118 else forms[i-119]
    for face in ['side','top']:
        put(A/f'textures/block/material_content/{path}_{face}.png', texture(i,form,face=='top'))
    textures={'side':f'chemicalelements:block/material_content/{path}_side','top':f'chemicalelements:block/material_content/{path}_top','particle':f'chemicalelements:block/material_content/{path}_side'}
    # Framed material display: recessed body, raised perimeter rails and numbered top cap.
    elements=[box([1,1,1],[15,15,15],'#side'),box([0,0,0],[16,2,16],'#side'),box([0,14,0],[16,16,16],'#top')]
    for x in (0,14):
        for z in (0,14): elements.append(box([x,2,z],[x+2,14,z+2],'#side'))
    put(A/f'models/block/{path}.json',{'textures':textures,'elements':elements})
    put(A/f'models/item/{path}.json',{'parent':f'chemicalelements:block/{path}'})
    put(A/f'blockstates/{path}.json',{'variants':{'':{'model':f'chemicalelements:block/{path}'}}})
    # No template drops: authoritative world pickup is not installed.
    put(D/f'loot_table/blocks/{path}.json',{'type':'minecraft:block','pools':[]})
for kind in ('block','item'):
    for tag,entries in {'candidates':ids,'elemental_candidates':ids[:118],'mixture_carriers':ids[118:],**{'carrier_'+f:['mixture_carrier_'+f] for f in forms}}.items():
        put(D/f'tags/{kind}/material_content/{tag}.json',{'replace':False,'values':['chemicalelements:'+v for v in entries]})
langs={}
for lang in ('en_us','zh_cn'):
    p=A/f'lang/{lang}.json'; raw=p.read_bytes(); old=json.loads(raw.decode('utf-8-sig')); new={}
    for name in names:
        label=old['item.chemicalelements.element_'+name]
        new['block.chemicalelements.elemental_'+name]=label+(' — Elemental candidate' if lang=='en_us' else '·单质形态候选')
    for f,en,zh in [('solid','Solid-form mixture carrier','固体形态混合载体'),('liquid','Liquid-form mixture carrier','液体形态混合载体'),('gas','Gas-form mixture carrier','气体形态混合载体'),('unresolved','Unresolved mixture carrier','未定形态混合载体')]:
        new['block.chemicalelements.mixture_carrier_'+f]=en if lang=='en_us' else zh
    new['tooltip.chemicalelements.material_content.blocked']='Placement withheld: %s' if lang=='en_us' else '未执行放置：%s'
    reasons={'no_authority':('Server authority required','需要服务端权威'),'invalid_target':('Unknown material carrier','未知材料载体'),'missing_ledger':('No authoritative instance ledger','缺少权威实例账本'),'quarantined':('Ledger quarantined','账本已隔离'),'count_mismatch':('Ledger/count mismatch','账本数量不匹配'),'stale_catalog':('Catalog migration required','需要目录版本迁移'),'empty_matter':('No accounted matter','未登记物质'),'invalid_composition':('Invalid composition','组成无效'),'identity_mismatch':('Element identity mismatch','元素身份不匹配'),'unknown':('Physical phase UNKNOWN; input retained','物理相态未知；保留输入'),'unmodeled':('Mixture/phase UNMODELED; input retained','混合物或相态无模型；保留输入'),'transaction_required':('Durable world transaction unavailable','耐久世界事务尚未接入')}
    for k,v in reasons.items(): new['tooltip.chemicalelements.material_content.status.'+k]=v[0 if lang=='en_us' else 1]
    assert not (old.keys() & new.keys()), 'language key conflict'
    langs[p]=(raw,old,new)
for p in out: assert not p.exists(), f'Concurrent/new-file conflict: {p}'
for p,b in out.items():
    p.parent.mkdir(parents=True,exist_ok=True)
    with p.open('xb') as f: f.write(b)
for p,(raw,old,new) in langs.items():
    assert p.read_bytes()==raw, 'Concurrent language write'
    # Append only: retain every original byte except the closing object delimiter.
    text=raw.decode('utf-8-sig'); end=text.rfind('}')
    added=json.dumps(new,ensure_ascii=False,indent=2)[1:-1]
    text=text[:end].rstrip()+','+added+'\n'+text[end:]
    assert all(json.loads(text)[k]==v for k,v in old.items())
    p.write_text(text,encoding='utf-8',newline='')
print(json.dumps({'created':len(out),'blocks':len(ids),'textures':len(ids)*2,'tags':14,'language_new_keys_each':len(new)}))
