import {test} from 'node:test';import assert from 'node:assert/strict';import {HandsFreeWard} from '../src/handsfree-core.ts';import {patients} from '../src/observations.ts';
function fixture(){const w=new HandsFreeWard();w.staff.forEach(s=>s.reachable=true);const context=w.apply('aino',{id:'p',type:'patient.confirm',patientId:'demo-a',encounterId:'enc-a'},'s').context!;const now=new Date().toISOString();const save={id:'save',type:'observation.save',...context,captureId:'capture',capturedAt:now,reviewedAt:now,profile:'HOS-BP-1/v1',values:{systolic:137,diastolic:82,pulse:71},original:{systolic:137,diastolic:82,pulse:71},units:{pressure:'mmHg',pulse:'bpm'},corrected:[],source:'injected-camera'};return{w,context,save};}
test('patient must be assigned; room and spoofed identities never authorize',()=>{const {w,save}=fixture();assert.equal(w.apply('sara',save,'s').ok,false);assert.equal(w.apply('aino',{id:'x',type:'patient.confirm',patientId:'12',encounterId:'enc-a'},'s').ok,false);assert.equal(w.apply('aino',save,'s').ok,false)});
test('save is immutable, idempotent by command and by capture, conflicting content denied',()=>{const{w,save}=fixture();const a=w.apply('aino',save,'s');assert.equal(a.ok,true);assert.deepEqual(w.apply('aino',save,'s'),a);assert.equal(w.apply('aino',{...save,id:'again'},'s').observationId,a.observationId);assert.equal(w.apply('aino',{...save,id:'conflict',values:{...save.values,pulse:72},corrected:['pulse']},'s').ok,false);assert.equal(w.observations.records.length,1)});
test('switch, clear, new session and disconnect invalidate late saves',()=>{for(const mode of ['switch','clear','disconnect','session']){const{w,save}=fixture();if(mode==='switch')w.apply('aino',{id:'switch',type:'patient.confirm',patientId:'demo-b',encounterId:'enc-b'},'s');if(mode==='clear')w.apply('aino',{id:'clear',type:'context.clear'},'s');if(mode==='disconnect')w.observations.clear('s');assert.equal(w.apply('aino',save,mode==='session'?'new':'s').ok,false);assert.equal(w.observations.records.length,0)}});
test('explicit corrections, units, complete tuple and capture clock validated',()=>{for(const patch of [{values:{systolic:1}},{units:{pressure:'kPa',pulse:'bpm'}},{values:{systolic:137,diastolic:82,pulse:72}},{capturedAt:'2099-01-01T00:00:00.000Z'},{measurementTime:new Date().toISOString()},{source:'fixture-expected-values'}]){const{w,save}=fixture();assert.equal(w.apply('aino',{...save,...patch},'s').ok,false)}const{w,save}=fixture();assert.equal(w.apply('aino',{...save,values:{...save.values,pulse:999},corrected:['pulse']},'s').ok,true);assert.equal(w.observations.records[0].measurementTime,null)});
test('save alone is private; separate share is minimal and participant-only',()=>{const{w,save,context}=fixture();const a=w.apply('aino',save,'s');assert.equal(w.snapshot('ben').observations.length,0);assert.equal(w.snapshot('sara').observations.length,0);const share={id:'share',type:'observation.share',...context,observationId:a.observationId,to:'ben',text:'Review bedside reading'};assert.equal(w.apply('aino',share,'s').ok,true);assert.equal(w.snapshot('ben').tasks[0].attachment?.values.pulse,71);assert.equal(w.snapshot('sara').tasks.length,0);assert.equal(w.snapshot('ben').tasks[0].attachment?.patientName,'Demo Patient A');assert.equal('original' in w.snapshot('ben').tasks[0].attachment!,false);assert.equal(w.apply('aino',{...share,id:'unauthorized',to:'sara'},'s').ok,false);assert.equal(w.apply('aino',{...share,id:'twice'},'s').ok,true);assert.equal(w.tasks.length,1)});
test('sharing never grants access to another patient, stale context or missing observation',()=>{const{w,context}=fixture();assert.equal(w.apply('aino',{id:'no',type:'observation.share',...context,observationId:'no',to:'ben',text:'Review'},'s').ok,false)});

test('server rejects superseded encounter and removed assignment even before client snapshot',()=>{const{w,save}=fixture();const p=patients[0];const encounter=p.encounterId;const assigned=[...p.staff];try{p.encounterId='new-encounter';assert.equal(w.apply('aino',save,'s').ok,false);p.encounterId=encounter;p.staff=[];assert.equal(w.apply('aino',{...save,id:'revoked'},'s').ok,false)}finally{p.encounterId=encounter;p.staff=assigned}});

test('recipient briefing preserves the exact corrected attachment through snapshot serialization', () => {
  const {w, save, context} = fixture();
  const saved = w.apply('aino', {...save, values: {...save.values, pulse: 72}, corrected: ['pulse']}, 's');
  w.apply('aino', {id:'share', type:'observation.share', ...context, observationId:saved.observationId, to:'ben', text:'Review bedside reading'}, 's');
  const snapshot = JSON.parse(JSON.stringify(w.snapshot('ben')));
  const a = snapshot.tasks[0].attachment;
  assert.deepEqual(a.values, {systolic:137, diastolic:82, pulse:72});
  assert.deepEqual(a.units, {pressure:'mmHg', pulse:'bpm'});
  assert.deepEqual(a.corrected, ['pulse']);
  assert.equal(a.capturedAt, save.capturedAt);
  assert.equal(a.measurementTime, null);
  assert.equal(a.source, 'injected-camera');
  assert.equal(snapshot.patients[0].warning, patients[0].warning);
  assert.equal(snapshot.patients[0].reported, patients[0].reported);
  assert.equal(snapshot.notes.length, 0);
  assert.equal(w.snapshot('sara').patients.some(p=>p.id==='demo-a'), false);
  assert.equal(w.snapshot('sara').tasks.length, 0);
});

test('assignment revocation redacts patient details without changing or deleting the shared task', () => {
  const {w, save, context} = fixture();
  const saved = w.apply('aino', save, 's');
  const shared = w.apply('aino', {id:'share', type:'observation.share', ...context, observationId:saved.observationId, to:'ben', text:'Review bedside reading'}, 's');
  const p = patients[0], original = [...p.staff];
  try {
    p.staff = ['aino'];
    const ben = w.snapshot('ben');
    assert.equal(ben.patients.some(p=>p.id==='demo-a'), false);
    assert.equal(ben.tasks[0].id, shared.taskId);
    assert.equal(ben.tasks[0].attachment, undefined);
    assert.equal(ben.tasks[0].attachmentUnavailable, true);
    assert.equal(ben.tasks[0].text, 'Patient-bound request');
    assert.equal(w.snapshot('aino').tasks[0].attachment?.values.pulse, 71);
    assert.ok(w.tasks[0].attachment, 'projection must not mutate stored attachment');
  } finally { p.staff = original; }
});

test('superseded encounter hides old readings, notes and request attachments', () => {
  const {w, save, context} = fixture();
  const saved = w.apply('aino', save, 's');
  assert.equal(w.apply('aino', {id:'note', type:'note.save', ...context, draftId:'draft', text:'Requested water.', source:'manual-dictation', reviewedAt:new Date().toISOString()}, 's').ok, true);
  w.apply('aino', {id:'share', type:'observation.share', ...context, observationId:saved.observationId, to:'ben', text:'Review bedside reading'}, 's');
  const p = patients[0], original = p.encounterId;
  try {
    p.encounterId = 'enc-next';
    const snapshot = w.snapshot('aino');
    assert.equal(snapshot.observations.length, 0);
    assert.equal(snapshot.notes.length, 0);
    assert.equal(snapshot.tasks[0].attachment, undefined);
    assert.equal(snapshot.tasks[0].attachmentUnavailable, true);
  } finally { p.encounterId = original; }
});
