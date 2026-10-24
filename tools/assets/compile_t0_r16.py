from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[2];O=R/'build/d-evidence/t0-31';O.mkdir(exist_ok=True)
old=(R/'build/d-evidence/javac.args').read_text(encoding='utf-8').splitlines();cp=old[old.index('"-classpath"')+1].strip('"')
sources=['src/main/java/org/goldgom/chemicalelements/machine/MachineTransactions.java','src/test/java/org/goldgom/chemicalelements/gametest/machine/MachineTransactionsTest.java']
def run(exe,args,name):
 p=O/(name+'.args');p.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
 x=subprocess.run([exe,'@'+str(p)],cwd=R,capture_output=True,text=True);(O/(name+'.log')).write_text(x.stdout+x.stderr,encoding='utf-8');print(x.stdout+x.stderr);print(name,x.returncode)
 if x.returncode:raise SystemExit(x.returncode)
run('D:/Java/jdk-21/bin/javac.exe',['-proc:none','--release','21','-d',O/'classes','-classpath',cp]+sources,'javac')
run('D:/Java/jdk-21/bin/java.exe',['-cp',str(O/'classes')+';'+cp,'org.goldgom.chemicalelements.gametest.machine.MachineTransactionsTest'],'types')
