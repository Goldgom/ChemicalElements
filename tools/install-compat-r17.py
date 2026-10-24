import json, urllib.request, pathlib, hashlib, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/'run'/'compat-r17'/'mods'
EVID=ROOT/'build'/'a-r17-compat-evidence'
DEST.mkdir(parents=True,exist_ok=True); EVID.mkdir(parents=True,exist_ok=True)
def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ChemicalElements-local-test/1.0'}),timeout=60).read()
records=[]
for slug,vid in [('curios','yohfFbgD'),('jade','eYz2YBGT'),('jei','rsnYBmN3')]:
    url='https://api.modrinth.com/v2/version/'+vid
    data=json.loads(fetch(url)); assert '1.21.1' in data['game_versions'] and 'neoforge' in data['loaders']
    f=next(f for f in data['files'] if f['primary']); target=DEST/f['filename']
    blob=target.read_bytes() if target.exists() else fetch(f['url'])
    assert hashlib.sha512(blob).hexdigest()==f['hashes']['sha512'], slug+' hash mismatch'
    if not target.exists():
        with target.open('xb') as out: out.write(blob)
    nested=[]
    with zipfile.ZipFile(target) as z:
        if 'META-INF/jarjar/metadata.json' in z.namelist(): nested=json.loads(z.read('META-INF/jarjar/metadata.json'))
    records.append({'mod':slug,'version':data['version_number'],'versionType':data['version_type'],'metadataUrl':url,'downloadUrl':f['url'],'path':str(target),'sha256':hashlib.sha256(blob).hexdigest(),'sha512':f['hashes']['sha512'],'nestedDependencies':nested,'metadata':data})
manifest=EVID/'install-manifest.json'
with manifest.open('w',encoding='utf-8') as out: json.dump(records,out,ensure_ascii=False,indent=2)
print(json.dumps([{k:r[k] for k in ['mod','version','versionType','path','sha256','nestedDependencies']} for r in records],ensure_ascii=False))
