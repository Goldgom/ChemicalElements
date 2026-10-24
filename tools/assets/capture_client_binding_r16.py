"""Read-only runtime capture; never launches a client or grants visual acceptance."""
from pathlib import Path
import hashlib,json,argparse
R=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--receipt',required=True);a=p.parse_args()
if not a.receipt.replace('-','').replace('_','').isalnum():raise SystemExit('invalid receipt name')
E=R/'build/d-evidence';out=E/(a.receipt+'.json')
if out.exists():raise SystemExit('receipt exists; use a new name')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
f=json.loads((E/'r16-current-source-fingerprint.json').read_text(encoding='utf-8'))
assets={k:v for k,v in f['files'].items() if k.startswith('src/main/resources/')}
checks=[]
for source,expected in sorted(assets.items()):
 src=R/source;runtime=R/'build/resources/main'/source.removeprefix('src/main/resources/')
 checks.append(dict(source=source,expected_sha256=expected,source_sha256=sha(src) if src.exists() else None,runtime_sha256=sha(runtime) if runtime.exists() else None))
log=R/'run/logs/latest.log';raw=log.read_bytes() if log.exists() else b''
# Store the observed log bytes: latest.log is mutable and a future hash alone cannot recover it.
logcopy=E/(a.receipt+'.observed.log');logcopy.write_bytes(raw)
text=raw.decode('utf-8',errors='replace');lines=text.splitlines()
issues=[l for l in lines if any(x in l.lower() for x in ('unable to load model','missing model','missing textures','using missing texture'))]
shots=sorted((R/'run/screenshots').glob('*.png'),key=lambda x:x.stat().st_mtime,reverse=True)[:8]
result=dict(schema=1,kind='runtime_binding_observation_not_visual_acceptance',source_fingerprint=f['source_fingerprint'],source_version=f['version'],asset_count=len(checks),all_assets_match=all(c['expected_sha256']==c['source_sha256']==c['runtime_sha256'] for c in checks),assets=checks,jars=[dict(path=str(x.relative_to(R)),sha256=sha(x)) for x in sorted((R/'build/libs').glob('*.jar'))],observed_log=dict(path=str(logcopy.relative_to(R)),sha256=hashlib.sha256(raw).hexdigest(),bytes=len(raw),first_line=lines[0] if lines else None,last_line=lines[-1] if lines else None,model_texture_issue_lines=issues,complete_run=False),recent_screenshots=[dict(path=str(x.relative_to(R)),sha256=sha(x),bytes=x.stat().st_size) for x in shots],client_verified=False,warning='Runtime resources are observed copies, not proof of active JVM loaded bytes. A must corroborate launch identity. Log is partial while client runs; zero matching lines is not full-run acceptance. Screenshots are inventoried only, not reviewed or assigned to this run.')
out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(output=str(out),assets=len(checks),all_assets_match=result['all_assets_match'],model_texture_issue_lines=len(issues),log_first_line=result['observed_log']['first_line'],log_last_line=result['observed_log']['last_line'],recent_screenshots=len(shots),client_verified=False),ensure_ascii=False))
