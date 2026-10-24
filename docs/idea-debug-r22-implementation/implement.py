from pathlib import Path
import hashlib, json, shutil, difflib, zipfile, xml.etree.ElementTree as ET, subprocess
ROOT = Path(r'E:\Projects\ChemicalElements')
OUT = ROOT / 'docs/idea-debug-r22-implementation'
DIAG = ROOT / 'docs/idea-debug-r22-diagnosis'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def write_new(p, b):
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open('xb') as f: f.write(b)
def text_new(p, s): write_new(p, s.encode('utf-8'))
assert sha(DIAG/'REPORT.md') == 'ADC2D5A39D7E996DF2F1C26D8074C9F2725A206528031EC090C1137E52A5F388'
for entry in json.loads((DIAG/'artifacts-sha256.json').read_text(encoding='utf-8-sig')):
    assert sha(Path(entry['path'])) == entry['sha256'], entry['path']
build = ROOT/'build.gradle'
before = build.read_bytes()
assert sha(build) == 'D90812F697B5FB467158DCF489C49396D1661C51D084FF1A1D34428A87C255B0'
protected = [ROOT/'.git/index', ROOT/'.idea/workspace.xml', *sorted((ROOT/'run/compat-r17/mods').glob('*.jar'))]
protected += sorted((ROOT/'build/moddev').glob('*'))
protected = [p for p in protected if p.is_file()]
guards = {str(p.relative_to(ROOT)): sha(p) for p in protected}
index_names = subprocess.run(['git','diff','--cached','--name-only'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.splitlines()
assert len(index_names) == 13
text_new(OUT/'protected-before.json', json.dumps({'files': guards,'staged_names':index_names},indent=2))
write_new(OUT/'build.gradle.before', before)
# Apply the already-reviewed candidate to an in-memory copy with exact hunk context.
s = before.decode('utf-8').replace('\r\n','\n')
lines = s.splitlines(keepends=True)
patch = (DIAG/'build.gradle.candidate.patch').read_text(encoding='utf-8').splitlines(keepends=True)
import re
pos=2
while pos < len(patch):
    assert patch[pos].startswith('@@ ')
    pos += 1
    old=[]; new=[]
    while pos<len(patch) and not patch[pos].startswith('@@ '):
        l=patch[pos]; pos+=1
        if l.startswith((' ','-')): old.append(l[1:])
        if l.startswith((' ','+')): new.append(l[1:])
    a=''.join(old); b=''.join(new)
    assert s.count(a)==1, a
    s=s.replace(a,b,1)
# Actual plugin source confirms tasksBefore is read by IntelliJIntegration and runClient,
# not by PrepareRun. Thus this edge survives generated Client import without a cycle.
a="tasks.register('prepareIdeaClient') {\n    dependsOn tasks.named('classes'), tasks.named('prepareClientRun')\n}\ntasks.named('runClient') {\n    dependsOn tasks.named('classes')\n}"
b="def prepareIdeaClient = tasks.register('prepareIdeaClient') {\n    group = 'mod development'\n    description = 'Compile current sources/resources and prepare client launch files; no JAR or tests.'\n    dependsOn tasks.named('classes'), tasks.named('prepareClientRun')\n}\n// ModDev regenerates its Client configuration on IDEA sync. Keep its prerequisite too.\n// Do not attach this to prepareClientRun itself: that would create a dependency cycle.\nneoForge.runs.named('client') {\n    taskBefore prepareIdeaClient\n}"
assert s.count(a)==1
s=s.replace(a,b)
xml=(DIAG/'Client_Latest.run.xml.candidate.txt').read_text(encoding='utf-8')
# Disable parallel GC worker scaling as well as Gradle task parallelism for desktop use.
xml=xml.replace(' -Dfml.modFolders=', ' -XX:ActiveProcessorCount=2 -Xmx2G -Dfml.modFolders=')
bare=xml.replace('Client - Latest Sources + Curios Jade JEI','Client - Latest Sources Bare (no optional mods)').replace('$PROJECT_DIR$/run/compat-r17','$PROJECT_DIR$/run/idea-debug-bare-r22').replace('scriptParameters="--max-workers=2 --no-parallel"','scriptParameters="--max-workers=2 --no-parallel -PideaWithoutOptionalMods=true"')
targets = {ROOT/'.run/Client_Latest_Sources.run.xml':xml,ROOT/'.run/Client_Latest_Sources_Bare.run.xml':bare}
for p,t in targets.items():
    assert not p.exists(),p
    e=ET.fromstring(t); assert e.find('./configuration/method/option').get('tasks')=='prepareIdeaClient'
# Save exact plugin evidence locally, without changing the cache or running Java.
zpath=next(Path('C:/Users/Goldgom/.gradle/caches/modules-2/files-2.1/net.neoforged/moddev-gradle/2.0.148').rglob('*sources.jar'))
with zipfile.ZipFile(zpath) as z:
    evidence=[]
    sections={'net/neoforged/moddevgradle/internal/IntelliJIntegration.java':['run.getIdeName().get()', 'run.getTasksBefore()', 'runConfigurations.add(appRun)', 'appRun.setWorkingDirectory'], 'net/neoforged/moddevgradle/internal/ModDevRunWorkflow.java':['task.dependsOn(prepareRunTask)', 'task.dependsOn(run.getTasksBefore())', 'tasks.register(InternalModelHelper.nameOfRun(run, "prepare"'], 'net/neoforged/moddevgradle/dsl/RunModel.java':['public void taskBefore(TaskProvider', 'getIdeName().convention']}
    for n,keys in sections.items():
        src=z.read(n).decode().splitlines()
        for i,line in enumerate(src):
            if any(k in line for k in keys):
                evidence.append(n+':'+str(i+1)+'\n'+'\n'.join(f'{j+1}: {src[j]}' for j in range(max(0,i-2),min(len(src),i+5))))
text_new(OUT/'moddev-generation-evidence.txt','Plugin sources jar SHA256 '+sha(zpath)+'\n\n'+'\n\n'.join(evidence))
# Guard immediately before write, preserving CRLF and refusing stale assumptions.
assert build.read_bytes()==before
for p,h in guards.items(): assert sha(ROOT/p)==h, p
with build.open('r+b') as f:
    assert f.read()==before
    f.seek(0); f.write(s.replace('\n','\r\n').encode()); f.truncate()
for p,t in targets.items(): text_new(p,t)
text_new(OUT/'applied.patch',''.join(difflib.unified_diff(before.decode().replace('\r\n','\n').splitlines(True),s.splitlines(True),fromfile='a/build.gradle',tofile='b/build.gradle')))
for p,h in guards.items(): assert sha(ROOT/p)==h,p
assert subprocess.run(['git','diff','--cached','--name-only'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.splitlines()==index_names
text_new(OUT/'protected-after.json', json.dumps({'files':{p:sha(ROOT/p) for p in guards},'staged_names':index_names},indent=2))
print('APPLIED build.gradle',sha(build))
for p in targets: print('CREATED',p,sha(p))
print('PROTECTED',len(guards),'MATCH; index 13 preserved; no Gradle/javac/client executed')
