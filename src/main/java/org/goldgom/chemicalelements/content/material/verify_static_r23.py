from pathlib import Path
import json, re, hashlib, struct, zlib, subprocess
ROOT=Path(__file__).resolve().parents[8]
assert ROOT==Path('E:/Projects/ChemicalElements')
A=ROOT/'src/main/resources/assets/chemicalelements'; D=ROOT/'src/main/resources/data/chemicalelements'
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def strict(p):
    def pairs(xs):
        d={}
        for k,v in xs:
            assert k not in d, (p,k)
            d[k]=v
        return d
    return json.loads(p.read_text(encoding='utf-8-sig'),object_pairs_hook=pairs)
source=(ROOT/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java').read_text()
names=re.findall(r'"([a-z]+)"',source.split('NAMES=List.of(')[1].split(');')[0]); assert len(names)==118
assert source.count('ElementMaterials.bootstrap(bus)')==1
# Restore ONLY the authorized insertion in memory and compare to the released original file hash.
p=ROOT/'src/main/java/org/goldgom/chemicalelements/registry/ElementItems.java'
original=p.read_bytes().replace(b'ElementMaterials.bootstrap(bus);',b'')
assert hashlib.sha256(original).hexdigest().upper()=='4E5ABAC20FCE1482B591CD2494F7E03AC0B96B439A50ED4280DF11207FD5ADF5'
ids=['elemental_'+n for n in names]+['mixture_carrier_'+f for f in ('solid','liquid','gas','unresolved')]
files=[p]; pngHashes=set(); mincolors=1024
for path in ids:
    paths=[A/f'blockstates/{path}.json',A/f'models/block/{path}.json',A/f'models/item/{path}.json',D/f'loot_table/blocks/{path}.json']
    state,model,item,loot=map(strict,paths)
    assert state['variants']['']['model']=='chemicalelements:block/'+path
    assert item['parent']=='chemicalelements:block/'+path
    assert len(model['elements'])==7 and loot['pools']==[]
    for face in ('side','top'):
        image=A/f'textures/block/material_content/{path}_{face}.png'; b=image.read_bytes()
        assert b[:8]==b'\x89PNG\r\n\x1a\n'
        pos=8; packed=b''
        while pos<len(b):
            size=struct.unpack('>I',b[pos:pos+4])[0]; tag=b[pos+4:pos+8]; data=b[pos+8:pos+8+size]
            assert zlib.crc32(tag+data)&0xffffffff==struct.unpack('>I',b[pos+8+size:pos+12+size])[0]
            if tag==b'IHDR': assert struct.unpack('>IIBBBBB',data)==(32,32,8,2,0,0,0)
            if tag==b'IDAT': packed+=data
            pos+=12+size
        raw=zlib.decompress(packed); assert len(raw)==32*97
        pixels=[]
        for y in range(32):
            assert raw[y*97]==0
            pixels.extend(tuple(raw[y*97+1+x*3:y*97+4+x*3]) for x in range(32))
        colors=len(set(pixels)); assert colors>20
        mincolors=min(mincolors,colors)
        assert digest(image) not in pngHashes, 'No reused textures'; pngHashes.add(digest(image)); files.append(image)
        assert model['textures'][face]=='chemicalelements:block/material_content/'+path+'_'+face
    files.extend(paths)
for kind in ('block','item'):
    directory=D/f'tags/{kind}/material_content'
    tags=list(directory.glob('*.json')); assert len(tags)==7
    for p in tags:
        data=strict(p); assert data['replace'] is False
        assert len(data['values'])==len(set(data['values'])) and all(x.split(':',1)[1] in ids for x in data['values'])
    assert len(strict(directory/'elemental_candidates.json')['values'])==118
    assert len(strict(directory/'mixture_carriers.json')['values'])==4
    files.extend(tags)
for lang in ('en_us','zh_cn'):
    p=A/f'lang/{lang}.json'; d=strict(p)
    for path in ids: assert d['block.chemicalelements.'+path]
    for name in names: assert d['item.chemicalelements.element_'+name]
    # Every tracked baseline language key still has its original value. Generator also checks all working-copy old keys before write.
    old=subprocess.run(['git','show','HEAD:'+p.relative_to(ROOT).as_posix()],cwd=ROOT,capture_output=True)
    if old.returncode==0:
        baseline=json.loads(old.stdout.decode('utf-8-sig'))
        assert all(k in d and d[k]==v for k,v in baseline.items())
    files.append(p)
for rel in ['src/main/java/org/goldgom/chemicalelements/content/material','src/main/java/org/goldgom/chemicalelements/gametest/material','src/test/java/org/goldgom/chemicalelements/content/material']:
    files.extend(p for p in (ROOT/rel).glob('*') if p.is_file())
files.extend(ROOT/x for x in ['src/main/java/org/goldgom/chemicalelements/registry/ElementMaterials.java','src/main/java/org/goldgom/chemicalelements/gametest/core/MaterialRegistrationGameTests.java'])
index=digest(ROOT/'.git/index'); assert index=='82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69'
staged=subprocess.check_output(['git','diff','--cached','--name-only'],cwd=ROOT).decode().splitlines(); assert len(staged)==13
assert not (ROOT/'.git/index.lock').exists()
check=subprocess.run(['git','diff','--check'],cwd=ROOT,capture_output=True); assert check.returncode==0,check.stderr
files.append(ROOT/'docs/material-content-r23/REPORT.md')
manifest={'version':'material-content-r23-v1','files':[{'path':p.relative_to(ROOT).as_posix(),'sha256':digest(p)} for p in sorted(set(files))]}
folder=ROOT/'docs/material-content-r23'; folder.mkdir(exist_ok=True)
for name,value in [('manifest.json',manifest),('static-results.json',{'version':manifest['version'],'blocks':122,'elements':118,'finite_carriers':4,'original_pngs':len(pngHashes),'minimum_distinct_rgb':mincolors,'tags':14,'files':len(manifest['files']),'index':index,'staged':13,'diff_check':0,'java_compiled':False,'gametest_run':False,'client_run':False})]:
    with (folder/name).open('x',encoding='utf-8') as f: json.dump(value,f,indent=2); f.write('\n')
print(json.dumps({'status':'STATIC_PASS','files':len(manifest['files']),'manifest_sha256':digest(folder/'manifest.json'),'min_colors':mincolors}))
