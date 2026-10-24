from pathlib import Path
import json,zipfile,re,itertools,collections
from decimal import Decimal
R=Path(__file__).resolve().parents[2];base=R/'src/main/resources/data/chemicalelements/chemicalelements'
species={p.stem:json.loads(p.read_text()) for p in (base/'species').glob('*.json')}
items={}
for p in (base/'item_compositions').glob('*.json'):
 d=json.loads(p.read_text());inv={}
 for s,n in d['per_instance'].items():
  q=int(Decimal(n)*10**18)
  for a,k in species[s.split(':')[1]]['atoms'].items():inv[a]=inv.get(a,0)+q*k
 for i in d['match']['items']:items[i]=inv
tags={}
for jar in (R/'build/moddev/artifacts').glob('*.jar'):
 with zipfile.ZipFile(jar) as z:
  for p in z.namelist():
   m=re.fullmatch(r'data/([^/]+)/tags/item/(.+)[.]json',p)
   if m:tags[m[1]+':'+m[2]]=json.loads(z.read(p)).get('values',[])
def expand(x,seen=frozenset()):
 if isinstance(x,list):return sorted(set(sum([expand(v,seen) for v in x],[])))
 if x.get('type')=='neoforge:difference':return sorted(set(expand(x['base'],seen))-set(expand(x['subtracted'],seen)))
 if 'item' in x:return [x['item']]
 t=x.get('tag')
 if not t or t in seen:return []
 out=[]
 for v in tags.get(t,[]):
  v=v.get('id') if isinstance(v,dict) else v
  out+=expand({'tag':v[1:]},seen|{t}) if v.startswith('#') else [v]
 return sorted(set(out))
rows=json.loads((R/'build/a-audit/vanilla-recipe-audit.json').read_text())
for r in rows:
 d=r['recipe'];typ=d['type'];parts=[]
 if typ=='minecraft:crafting_shaped':parts=[d['key'][c] for line in d['pattern'] for c in line if c!=' ']
 elif typ=='minecraft:crafting_shapeless':parts=d['ingredients']
 elif typ in ['minecraft:smelting','minecraft:blasting','minecraft:smoking','minecraft:campfire_cooking','minecraft:stonecutting']:parts=[d['ingredient']]
 elif typ=='minecraft:smithing_transform':parts=[d[k] for k in ['template','base','addition']]
 else:r.update(status='dynamic_semantics_required',reason='Special recipe uses item components/runtime inventory; requires dedicated B transaction rewrite, not JSON fixed-result assumption.');continue
 result=d.get('result');target=result if isinstance(result,str) else result.get('id');count=d.get('count',1) if isinstance(result,str) else result.get('count',1)
 variants=[];missing=[]
 for part in parts:
  opts=expand(part);unique={}
  for item in opts:
   if item not in items:missing.append(item);continue
   # Return unconsumed empty containers in output accounting.
   net=dict(items[item]);rem='minecraft:bucket' if item.endswith('_bucket') else 'minecraft:glass_bottle' if item=='minecraft:honey_bottle' else None
   if rem:
    for k,v in items[rem].items():net[k]=net.get(k,0)-v
   unique[tuple(sorted(net.items()))]=item
  if not unique:missing.append(str(part))
  variants.append(list(unique))
 if target not in items or missing:r.update(status='unresolved_ingredients',missing=missing,reason='No default composition fallback.');continue
 combinations=1
 for v in variants:combinations*=len(v)
 if combinations>100000:r.update(status='variant_space_requires_symbolic_audit',variant_combinations=combinations,reason='No truncation reported as complete.');continue
 balances=[]
 for choice in itertools.product(*variants):
  delta={k:-v*count for k,v in items[target].items()}
  for inv in choice:
   for k,v in inv:delta[k]=delta.get(k,0)+v
  delta={k:str(v) for k,v in delta.items() if v}
  if delta not in balances:balances.append(delta)
 exact=all(not b for b in balances);deficit=any(any(int(v)<0 for v in b.values()) for b in balances)
 r.update(status='balanced_inventory_only' if exact else 'requires_input_or_output_rewrite' if deficit else 'requires_residue_output',variant_combinations=combinations,element_delta_quanta=balances,reason='Positive=input excess requiring explicit residue; negative=missing atoms. Feasibility, temperature, time, charge and all runtime component variants still require engine and transaction validation.')
(R/'build/a-audit/vanilla-recipe-balance.json').write_text(json.dumps(rows,indent=2))
counts=dict(collections.Counter(r['status'] for r in rows));(R/'build/a-audit/recipe-balance-summary.json').write_text(json.dumps(counts,indent=2));print(counts)
