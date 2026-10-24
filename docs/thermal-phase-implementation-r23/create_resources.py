import json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/data/chemicalelements/chemicalelements/phase_models'
def base(name,subject,kind,reason):
 return dict(schema_version=1,id='chemicalelements:'+name,subject='chemicalelements:'+subject,allotrope='UNSPECIFIED_NOT_ASSUMED',kind=kind,reason=reason,domain=None,energy_reference=None,low_species=None,high_species=None,transition_k=None,cp_low_j_mol_k=None,cp_high_j_mol_k=None,latent_j_mol=None,legacy_operation=None,sources=[])
docs={}
coverage=json.loads((ROOT/'docs/material-phase-r23/coverage-118.json').read_text(encoding='utf-8-sig'))
# Element resource filenames are IDs, not external physical data.
for p in sorted((ROOT/'src/main/resources/data/chemicalelements/chemicalelements/elements').glob('*.json')):
 name=p.stem
 docs[name+'_unknown']=base(name+'_unknown',name,'UNKNOWN','Atomic bookkeeping species is not a validated thermodynamic identity/allotrope; complete sourced enthalpy model pending. No physical phase inferred.')
for op,subject in [('melt','ice'),('freeze','water'),('vaporize','water'),('condense','water_vapor')]:
 d=base('water_'+op+'_legacy',subject,'LEGACY_WATER_LATENT','Existing narrow water latent-only model retained. No new Cp, energy reference, density, EOS or production activation.')
 d['legacy_operation']=op
 d['sources']=[dict(id='existing-water-code',uri='urn:chemicalelements:legacy:WaterPhaseModels',citation='Existing WaterPhaseModels.java; not a newly verified primary thermochemical source',locator='WaterPhaseModels.'+op+'()',accessed='2026-10-24',basis='UNVERIFIED',method='Existing approximate latent constants; not reclassified as measured',pressure_evidence='Existing 101000..101650 Pa game tolerance, not primary-source pressure verification',uncertainty='Not established by this batch',license_status='EXISTING_CODE_ONLY',units='K;Pa;J/mol;J/mol/K',limitations='Adapter only; external source permission and full enthalpy data remain pending; no constants copied into resources')]
 docs['water_'+op+'_legacy']=d
assert len(docs)==122
OUT.mkdir(parents=True,exist_ok=True)
for name in docs:
 if (OUT/(name+'.json')).exists(): raise SystemExit('Refuse overwrite: '+name)
for name,d in docs.items():
 with (OUT/(name+'.json')).open('x',encoding='utf-8',newline='\n') as f: f.write(json.dumps(d,indent=2)+'\n')
print('Created 118 UNKNOWN + 4 existing-code adapters; 0 complete production enthalpy datasets')
