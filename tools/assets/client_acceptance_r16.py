"""Inventory only: never converts static existence into visual acceptance.
Existing evidence is retained in separate append-only reviewer receipts, not overwritten here.
No game launch, build, filesystem traversal outside the selected project, or network.
"""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[2]
A=R/'src/main/resources/assets/chemicalelements'
E=R/'build/d-evidence'
fingerprint=json.loads((E/'r16-current-source-fingerprint.json').read_text())
rows=[]
def add(key,kind,path,scenario):
 rows.append(dict(id=key,kind=kind,source=path.relative_to(R).as_posix() if path else None,
  sha256=hashlib.sha256(path.read_bytes()).hexdigest() if path else None,
  scenario=scenario,status='NOT_VERIFIED',screenshot=None,client_run=None))
for p in sorted((A/'models/item').glob('*.json')):
 add('item.'+p.stem,'item',p,'Inventory and hand: silhouette, original texture, readable scale, no missing texture/model; include localized hover name.')
for p in sorted((A/'blockstates').glob('*.json')):
 states=json.loads(p.read_text()).get('variants',{})
 for state in sorted(states):
  add('block.'+p.stem+'['+state+']','blockstate',p,'Loaded in-world state '+state+'; inspect UV, orientation and adjacent seams. Set running states only through actual server behavior, never fake output.')
for p in sorted((A/'textures/gui').glob('*.png')):
 add('gui.'+p.stem,'gui',p,'Real GUI entry point: small/large window, text/slot alignment, controls and data readability; texture preview is not evidence.')
for key,scenario in {
 'assembly.six_sizes':'Place all six part dimensions at four facings; inspect complete silhouette, layered seams and one-item break drop.',
 'assembly.tower':'Assemble real 3x6x3 tower; inspect base/core/reflux/top functional partitions, all four facings, then break one required cell.',
 'machine.all_25':'Each of 25 machines: correct structure, missing-part stop, actual processing/pause status and server-supplied GUI/Jade values.',
 'detector.small_window':'320/426 scaled width: all five bottom controls visible and clickable; compare wide layout.',
 'detector.changed_target':'Navigate to later instance/component page then change target or active equipment tier; page and scroll return to zero.',
 'detector.unequip':'Unequip while detail page is open: local clear and server rejection; delayed old reply cannot restore contents.',
 'detector.downgrade':'Downgrade research to basic: clear old details, hide instance/nuclide data, retain server authorization rules.',
 'detector.reload':'Reload server profiles and catalog: revoked authorization clears, failed reload preserves valid atomic catalog.',
 'detector.reconnect':'Close/reopen/reconnect: old session/request/emission never restores stale details.',
 'detector.no_curios':'Launch optional-dependency-absent build: explicit unavailable state, no hand-held authorization bypass.',
 'compat.jade_jei':'Real Jade/JEI entry points, localized config labels and custom panel alignment, no plugin model warnings.',
 'logs.models':'Per-client-run complete latest.log model/texture WARN review; record run identity and every nonzero issue, not old log or only grep count.'
}.items():add(key,'scenario',None,scenario)
result=dict(schema=1,source_fingerprint=fingerprint['source_fingerprint'],source_version=fingerprint['version'],
 client_verified=False,warning='Inventory is a test checklist, not visual or gameplay acceptance. Every row starts NOT_VERIFIED. Evidence receipts must cite matching run/resource fingerprint and screenshot paths.',
 rows=rows)
p=E/'r16-client-acceptance-inventory.json'
# A source-versioned filename preserves prior lists rather than replacing reviewer work.
p=E/('r16-client-acceptance-'+fingerprint['source_fingerprint'][:12]+'.json')
text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
if p.exists():
 if p.read_text(encoding='utf-8')!=text:raise SystemExit('existing version differs; retained without overwrite')
else:p.write_text(text,encoding='utf-8')
print(json.dumps({'output':str(p),'rows':len(rows),'by_kind':{k:sum(r['kind']==k for r in rows) for k in sorted({r['kind'] for r in rows})},'all_not_verified':all(r['status']=='NOT_VERIFIED' for r in rows),'source_fingerprint':fingerprint['source_fingerprint']},ensure_ascii=False))
