import json, pathlib, hashlib, re, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
DOC=ROOT/'docs/material-reload-r23'
RES=ROOT/'src/main/resources/data/chemicalelements/chemicalelements'
def strict(p):
    def pairs(items):
        d={}
        for k,v in items:
            assert k not in d, (p,'duplicate key',k)
            d[k]=v
        return d
    return json.loads(p.read_text(encoding='utf-8-sig'),object_pairs_hook=pairs)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
schema=strict(DOC/'material-behavior.schema.json')
models={strict(p)['id']:strict(p) for p in (RES/'phase_models').glob('*.json')}
species={'chemicalelements:'+p.stem for p in (RES/'species').glob('*.json')}
checks=[]
for p in sorted((RES/'material_behaviors').glob('*.json')):
    o=strict(p)
    assert set(o)==set(schema['required'])
    for k,rule in schema['properties'].items():
        v=o[k]
        if 'const' in rule:assert v==rule['const']
        if 'enum' in rule:assert v in rule['enum']
        if isinstance(v,str):
            assert len(v)>=rule.get('minLength',0) and len(v)<=rule.get('maxLength',100000)
            if 'pattern' in rule:assert re.fullmatch(rule['pattern'],v)
        if rule.get('type')=='array':
            assert isinstance(v,list) and len(v)==len(set(v))
            assert rule.get('minItems',0)<=len(v)<=rule['maxItems']
            assert all(isinstance(x,str) and re.fullmatch(rule['items']['pattern'],x) for x in v)
    assert o['id']=='chemicalelements:'+p.stem
    assert set(o['all_species'])<=species
    if o['phase_model'] is not None:
        m=models[o['phase_model']]
        assert o['composition_kind']=='PURE' and o['all_species']==[m['subject']]
    checks.append(p.name)
assert len(checks)==3
# Verify duplicate-fixture mutations really modify the shipped source text.
raw=(RES/'material_behaviors/iron_model.json').read_text()
assert '"schema_version": 1' in raw
assert '"kind": "UNKNOWN"' in (RES/'phase_models/iron_unknown.json').read_text()
index=sha(ROOT/'.git/index')
assert index=='82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69'
staged=subprocess.check_output(['git','diff','--cached','--name-only'],cwd=ROOT,text=True).splitlines()
assert len(staged)==13
base='src/main/java/org/goldgom/chemicalelements/'
paths=[base+'data/'+n+'.java' for n in ['AtomicChemistryCatalog','ChemistryBundle','ChemistryReloadListener','MaterialBehaviorDefinition','MaterialBehaviorLibrary']]
paths += [base+'gametest/core/MaterialReloadGameTests.java','src/test/java/org/goldgom/chemicalelements/data/MaterialBehaviorLibraryTest.java']
paths += [str(p.relative_to(ROOT)).replace('\\','/') for p in sorted((RES/'material_behaviors').glob('*.json'))]
paths += ['docs/material-reload-r23/'+n for n in ['material-behavior.schema.json','verify_static.py','REPORT.md']]
result=subprocess.run(['git','diff','--check','--']+paths,cwd=ROOT,capture_output=True,text=True)
assert result.returncode==0,result.stdout+result.stderr
# Exact produced files, excluding self-referential manifest and result; no output trees scanned/written.
manifest={'version':'material-reload-r23-v1','compiled':False,'gameTestsRun':False,'files':[{'path':p,'sha256':sha(ROOT/p)} for p in paths]}
(DOC/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
result={'status':'PASS','schemaCheck':'structure/rules used here plus cross-reference semantics; not a general JSON Schema implementation','resources':checks,'phaseResourcesReadOnly':len(models),'indexSha256':index,'stagedCount':len(staged),'diffCheckExit':0,'manifestSha256':sha(DOC/'manifest.json'),'compiled':False,'gameTestsRun':False}
(DOC/'static-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
