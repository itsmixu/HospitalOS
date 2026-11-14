import test from 'node:test';
import assert from 'node:assert/strict';
import {GuidedDemo,TiltInput,NOTE,QR,ISBAR,VALUES,CARDS,type Phase} from './guided-model';
import {patients} from '../../server/src/observations.ts';
const finish=(d:GuidedDemo)=>{assert.ok(d.duration>0,`No timer at ${d.phase}`);d.tick(d.duration);assert.notEqual(d.phase,'error',d.error);};
function go(d:GuidedDemo,phase:Phase){
 for(let n=0;d.phase!==phase&&n<50;n++){
  if(d.duration)finish(d);
  else if(d.phase==='patient-confirm'||d.phase==='review'||d.phase==='emergency')d.tilt('right');
  else if(d.phase==='request')d.tilt('left');
  else d.primary();
  assert.notEqual(d.phase,'error',d.error);
 }
 assert.equal(d.phase,phase);
}
test('full walkthrough binds patient, declines request, reviews two records and scopes ISBAR',()=>{
 const d=new GuidedDemo();go(d,'patient-confirm');assert.equal(d.snap.context,null);assert.equal(d.snap.observations.length,0);
 d.tilt('right');assert.equal(d.patient?.name,'Mark Pokidko');assert.equal(d.patient?.room,'7');assert.equal(d.ward.snapshot(d.actor).context?.encounterId,'enc-a');
 go(d,'request');assert.equal(d.snap.staff.find(s=>s.id===d.actor)?.status,'busy');assert.equal(d.ward.snapshot('ben').tasks[0].status,'delivered');
 d.tilt('left');assert.equal(d.ward.snapshot('ben').tasks[0].status,'declined');assert.equal(d.requestResponse,'declined');
 go(d,'review');assert.equal(d.snap.observations.length,0);assert.equal(d.snap.notes.length,0);assert.ok(d.pending);assert.ok(d.medicationGiven);
 const prompt=d.prompt;d.tilt('right',prompt);d.tilt('right',prompt);assert.equal(d.phase,'saved');assert.equal(d.snap.observations.length,1);assert.equal(d.snap.notes.length,1);
 assert.deepEqual(d.snap.observations[0].values,VALUES);assert.equal(d.snap.notes[0].text,NOTE);assert.equal(d.snap.notes[0].author,'jooyeon');assert.equal(d.snap.notes[0].eventTime,null);
 go(d,'emergency');assert.equal(d.snap.emergencies[0].isbar,undefined);assert.equal(d.snap.patients.some(p=>p.id==='demo-c'),false);
 d.tilt('right');assert.deepEqual(d.snap.emergencies[0].isbar,ISBAR);assert.equal(d.snap.emergencies[0].response,'acknowledged');assert.equal(d.snap.patients.some(p=>p.id==='demo-c'),false);
 d.primary();assert.equal(d.phase,'handover','must allow time to read');d.tick(11000);d.primary();finish(d);assert.equal(d.phase,'complete');
});
test('a scan is identification only, can be cancelled, and rejects another QR',()=>{
 const d=new GuidedDemo();go(d,'scan-ready');assert.throws(()=>d.scan('hospitalos:patient:demo-b:enc-b'));d.scan(QR);d.tilt('left');assert.equal(d.phase,'scan-ready');assert.equal(d.snap.context,null);
 go(d,'patient-confirm');const stale=d.prompt;d.tilt('left');d.tilt('right',stale);assert.equal(d.snap.context,null);go(d,'overview');d.tilt('left');assert.equal(d.snap.context,null);
});
test('capture cancellation never creates pending or saved data',()=>{const d=new GuidedDemo();go(d,'capturing');d.tilt('left');assert.equal(d.phase,'capture-ready');assert.equal(d.pending,null);assert.equal(d.snap.observations.length,0);go(d,'reading');assert.ok(d.pending);});
test('back from dictation and review preserves only the draft',()=>{
 const d=new GuidedDemo();go(d,'transcribing');d.tick(5000);d.tilt('left');assert.equal(d.phase,'note-paused');d.tick(60000);assert.equal(d.snap.notes.length,0);d.primary();assert.equal(d.phase,'transcribing');finish(d);
 const prompt=d.prompt;d.tilt('left');d.tilt('right',prompt);assert.equal(d.snap.notes.length,0);d.primary();assert.equal(d.phase,'review');d.tilt('right');assert.equal(d.snap.notes.length,1);
});
test('stale patient binding rejects all writes',()=>{const d=new GuidedDemo();go(d,'review');d.apply({type:'context.clear'});d.tilt('right');assert.equal(d.phase,'error');assert.match(d.error,/context changed/);assert.equal(d.snap.observations.length,0);assert.equal(d.snap.notes.length,0);});
test('a partial save is explicit and retry does not duplicate the successful record',()=>{
 const d=new GuidedDemo();go(d,'review');const save=d.ward.patientNotes.save.bind(d.ward.patientNotes);d.ward.patientNotes.save=()=> 'Simulated note-store failure';
 d.tilt('right');assert.equal(d.phase,'error');assert.equal(d.snap.observations.length,1);assert.equal(d.snap.notes.length,0);assert.ok(d.savedObservation);assert.equal(d.savedNote,'');
 d.ward.patientNotes.save=save;d.primary();assert.equal(d.phase,'review');d.tilt('right');assert.equal(d.phase,'saved');assert.equal(d.snap.observations.length,1);assert.equal(d.snap.notes.length,1);
});
test('request accept is a valid alternative, no invented decline receipt',()=>{const d=new GuidedDemo();go(d,'request');d.tilt('right');assert.equal(d.requestResponse,'accepted');assert.equal(d.ward.snapshot('ben').tasks[0].status,'accepted');});
test('emergency back hides only; accepting later unlocks briefing',()=>{const d=new GuidedDemo();go(d,'emergency');const p=d.prompt;d.tilt('left');d.tilt('right',p);assert.equal(d.snap.emergencies[0].response,'pending');assert.equal(d.snap.emergencies[0].isbar,undefined);d.primary();d.tilt('right');assert.equal(d.snap.emergencies[0].response,'acknowledged');});
test('repeated primary inputs cannot duplicate scans, captures or medication',()=>{const d=new GuidedDemo();go(d,'scan-ready');d.primary();for(let n=0;n<10;n++)d.primary();assert.equal(d.phase,'scanning');go(d,'capturing');for(let n=0;n<10;n++)d.primary();assert.equal(d.pending,null);go(d,'review');for(let n=0;n<10;n++)d.primary();assert.equal(d.snap.notes.length,0);});
test('replay clears the complete session, stale gestures cannot act in new run',()=>{
 const d=new GuidedDemo();go(d,'handover');const stale=d.prompt;d.restart();assert.equal(d.phase,'ready');assert.equal(d.pending,null);assert.equal(d.snap.context,null);assert.equal(d.snap.notes.length,0);assert.equal(d.snap.tasks.length,0);assert.equal(d.snap.emergencies.length,0);go(d,'patient-confirm');d.tilt('right',stale);assert.equal(d.phase,'patient-confirm');go(d,'saved');assert.equal(d.snap.notes.length,1);
});
test('scenario instances are isolated and shared Android fixtures are untouched',()=>{const a=new GuidedDemo(),b=new GuidedDemo();go(a,'saved');assert.equal(b.snap.notes.length,0);assert.equal(patients.find(p=>p.id==='demo-a')?.room,'12');assert.equal(patients.find(p=>p.id==='demo-a')?.name,'Demo Patient A');});
test('partial tilt retreats without action; held tilt fires once and needs neutral',()=>{
 const actions:string[]=[];const t=new TiltInput(d=>actions.push(d));t.press('left',1);t.tick(400,1);assert.ok(t.progress>0&&t.progress<1);t.release();t.tick(300,1);assert.equal(t.progress,0);assert.deepEqual(actions,[]);
 t.press('right',1);t.tick(900,1);t.tick(2000,1);assert.deepEqual(actions,['right']);t.release();t.tick(100,1);t.press('left',1);assert.equal(t.direction,null);t.tick(300,1);t.press('left',1);t.tick(850,1);assert.deepEqual(actions,['right','left']);
});
test('a changed prompt, blur cancellation and conflicting inputs cannot submit',()=>{let count=0;const t=new TiltInput(()=>count++);t.press('right',1);t.tick(400,1);t.tick(500,2);assert.equal(count,0);t.tick(300,2);t.press('left',2);t.press('right',2);assert.equal(t.direction,'left');t.cancel();t.tick(1000,2);assert.equal(count,0);});
test('time never authorizes a patient, note, request or emergency response',()=>{for(const phase of ['patient-confirm','request','review','emergency'] as const){const d=new GuidedDemo();go(d,phase);const snapshot=JSON.stringify(d.snap);d.tick(120000);assert.equal(d.phase,phase);assert.equal(JSON.stringify(d.snap),snapshot);}});

test('saved note is the exact requested sentence, without display line breaks',()=>{assert.equal(CARDS.review.lines.join(' '),NOTE);assert.equal(NOTE,'40mg Orion Furosemide 1x tab given to Mark Pokidko by nurse Jooyeon Lee at 15:30 for high blood pressure.');});
test('one held save gesture cannot accept the next emergency prompt',()=>{
 const d=new GuidedDemo();go(d,'review');const input=new TiltInput((direction,prompt)=>d.tilt(direction,prompt));
 input.press('right',d.prompt);input.tick(850,d.prompt);assert.equal(d.phase,'saved');
 finish(d);assert.equal(d.phase,'emergency');input.tick(10000,d.prompt);assert.equal(d.snap.emergencies[0].response,'pending');
 input.release();input.tick(300,d.prompt);input.press('right',d.prompt);input.tick(850,d.prompt);assert.equal(d.snap.emergencies[0].response,'acknowledged');
});
