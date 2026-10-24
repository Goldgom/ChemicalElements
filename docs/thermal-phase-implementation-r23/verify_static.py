import json,pathlib,hashlib,subprocess
from decimal import Decimal
R=pathlib.Path(__file__).resolve().parents[2]; D=pathlib.Path(__file__).parent
res=R/'src/main/resources/data/chemicalelements/chemicalelements/phase_models'
string={'type':'string','minLength':1,'maxLength':4096}
num={'type':'string','pattern':r'^-?(0|[1-9][0-9]*)(\.[0-9]+)?$','maxLength':80}
def obj(p):return {'type':'object','additionalProperties':False,'required':list(p),'properties':p}
def nullable(p):return {'anyOf':[{'type':'null'},p]}
source=obj({k:string for k in ['id','uri','citation','locator','accessed','basis','method','pressure_evidence','uncertainty','license_status','units','limitations']})
source['properties']['basis']={'enum':['MEASURED','EVALUATED','PREDICTED','GAME_CONVENTION','UNVERIFIED']}
source['properties']['license_status']={'enum':['CLEARED','NOT_CLEARED','UNKNOWN','EXISTING_CODE_ONLY']}
source['properties']['units']={'const':'K;Pa;J/mol;J/mol/K'}
domain=obj({**{k:num for k in ['min_k','max_k','min_pa','max_pa']},'ensemble':{'const':'ISOBARIC_APPROXIMATION'}})
p={k:string for k in ['id','subject','allotrope','reason']}
p.update(schema_version={'const':1},kind={'enum':['UNKNOWN','UNMODELED','LEGACY_WATER_LATENT','CONSTANT_CP_TWO_PHASE']},domain=nullable(domain),sources={'type':'array','maxItems':256,'items':source})
p.update({k:nullable(string) for k in ['energy_reference','low_species','high_species','legacy_operation']})
p.update({k:nullable(num) for k in ['transition_k','cp_low_j_mol_k','cp_high_j_mol_k','latent_j_mol']})
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','title':'PhaseLibrary v1 structure; Java semantic validation additionally mandatory',**obj(p)}
with (D/'phase-model.schema.json').open('x',encoding='utf-8') as f:json.dump(schema,f,indent=2)
# Dependency-free validator for the exact emitted schema subset; not advertised as full JSON Schema implementation.
def validate(v,s):
 if 'anyOf'in s:
  for branch in s['anyOf']:
   try:validate(v,branch);return
   except AssertionError:pass
  raise AssertionError('no union branch')
 if 'const'in s:assert v==s['const']
 if 'enum'in s:assert v in s['enum']
 t=s.get('type')
 if t=='null':assert v is None
 if t=='object':
  assert isinstance(v,dict) and set(v)==set(s['required'])
  for k,value in v.items():validate(value,s['properties'][k])
 if t=='array':
  assert isinstance(v,list) and len(v)<=s.get('maxItems',10000)
  for x in v:validate(x,s['items'])
 if t=='string':
  import re
  assert isinstance(v,str) and s.get('minLength',0)<=len(v)<=s.get('maxLength',10000)
  if 'pattern'in s:assert re.fullmatch(s['pattern'],v)
files=list(res.glob('*.json')); assert len(files)==122
counts={}; errors=[]
for f in files:
 d=json.loads(f.read_text(encoding='utf-8'));validate(d,schema)
 assert d['id']=='chemicalelements:'+f.stem
 assert (R/'src/main/resources/data/chemicalelements/chemicalelements/species'/ (d['subject'].split(':')[1]+'.json')).exists()
 counts[d['kind']]=counts.get(d['kind'],0)+1
 assert all(d[k] is None for k in ['domain','energy_reference','low_species','high_species','transition_k','cp_low_j_mol_k','cp_high_j_mol_k','latent_j_mol'])
 if d['kind']=='LEGACY_WATER_LATENT': assert d['sources'] and d['legacy_operation'] in ['melt','freeze','vaporize','condense']
# Schema rejection checks, independently of Java (Java NOT executed).
d=json.loads(files[0].read_text());bad=dict(d);bad['extra']=1
for bad in [bad,{k:v for k,v in d.items() if k!='subject'}]:
 try:validate(bad,schema)
 except AssertionError:continue
 raise AssertionError('invalid accepted')
q=Decimal('1e-18');latent=Decimal('10')
for budget in [Decimal(0),q,q*10,Decimal(5),Decimal(10),Decimal(50)]:
 n=min(int(budget/latent/q),10**18);used=Decimal(n)*q*latent
 assert used<=budget and Decimal(n)*q+(Decimal(1)-Decimal(n)*q)==1
index=hashlib.sha256((R/'.git/index').read_bytes()).hexdigest()
assert index=='82db786aa6a4f64d0b38f02be6e7eaf7a1d35e9e905e7efe0c29713593cf1b69'
staged=subprocess.check_output(['git','diff','--cached','--name-only'],cwd=R,text=True).splitlines();assert len(staged)==13
results={'resources':len(files),'kinds':counts,'schema_subset_validation':'PASS','required_and_extra_field_rejection':'PASS','decimal_latent_budget_reference_check':'PASS (Python arithmetic only, not Java)','index_sha256':index,'staged_count':len(staged),'java_compiled':False,'java_tests_run':False,'gametest_run':False,'live_reload_installed':False}
with (D/'static-results.json').open('x',encoding='utf-8') as f:json.dump(results,f,indent=2)
print(json.dumps(results,indent=2))
