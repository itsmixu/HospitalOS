import {GuidedDemo,TiltInput,CARDS,NOTE,type Phase,type Direction} from './guided-model';
import {createWard,type Shot} from './ward';
const el=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const demo=new GuidedDemo();
const tilt=new TiltInput((direction,prompt)=>demo.tilt(direction,prompt));
let ward:Awaited<ReturnType<typeof createWard>>|undefined;
let loaded=false,reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
let activePointer:number|null=null,activeKey='',elapsedTime=0,lastTime=0,frameTime=0;
const titles=['Meet your patient','Stay with the patient','Review. Then save.','Respond with context'];
const instructions:Record<Phase,string>={
 ready:'A guided experience. You make the important decisions.',intro:'Your hands save lives. Keep them free.',approach:'Approaching your patient in Room 7.',
 'scan-ready':'Look at the wristband. Scan to identify your patient.',scanning:'Reading the wristband on Mark’s wrist…','patient-confirm':'Check the identity, then hold right to confirm.',overview:'Your patient, their prescription and your assignment — at a glance.',
 procedure:'Starting the blood-pressure check.',request:'You’re in a procedure. Hold left to decline Ben’s request.',receipt:'Ben receives your response. Your work continues.',measuring:'The cuff is measuring. Your hands stay with the patient.','capture-ready':'The reading is ready. Capture it from the monitor.',capturing:'Capturing the simulated monitor…',reading:'The reading is pending. Next, give the already prescribed tablet.',medication:'Giving the medication already prescribed for Mark.',
 'note-ready':'Now document what you did — hands free.',transcribing:'The nurse dictates. Lumi prepares the draft.','note-paused':'Draft paused. Nothing else has been saved.',review:'Review the reading and note. Hold right to save both.',saved:'Both records are confirmed. Your hands are still free.',
 emergency:'Code Blue in Room 8. Hold right to accept the response.','emergency-paused':'The alert is still available. No response has been sent.',handover:'Read the team’s handover as you head toward Room 8.',outro:'Every second matters.',complete:'Patient information. Reviewed records. Prioritized communication.',error:'The action wasn’t completed. Your existing records are preserved.',
};
const captions:Partial<Record<Phase,string>>={procedure:'Checking blood pressure',measuring:'Blood-pressure check in progress',medication:'Prescribed: 40mg Orion Furosemide · 1 tablet','note-ready':'“Hey Lumi, add patient note”','note-paused':'Draft retained · not authorized',handover:'Heading to Room 8'};
const waitLabels:Partial<Record<Phase,string>>={intro:'Beginning',approach:'Approaching bedside',scanning:'Scanning',procedure:'Checking blood pressure',receipt:'Response delivered',measuring:'Measuring',capturing:'Capturing',medication:'Giving prescribed tablet',transcribing:'Nurse dictating',saved:'Records saved',outro:'Lumi'};
function shotFor():Shot{
 if(['ready','intro'].includes(demo.phase))return 'wide';
 if(['approach','scan-ready','scanning','patient-confirm'].includes(demo.phase))return 'wrist';
 if(['overview','procedure','request','receipt'].includes(demo.phase))return 'bedside';
 if(['measuring','capture-ready','capturing','reading'].includes(demo.phase))return 'monitor';
 if(['medication','note-ready','transcribing','note-paused','review','saved','emergency','emergency-paused'].includes(demo.phase))return 'medicine';
 if(demo.phase==='handover')return demo.elapsed<3500?'exit':'corridor';
 return 'corridor';
}
function lines(body:HTMLElement,text:string[]){for(const line of text){const p=document.createElement('p');p.textContent=line;body.append(p)}}
function showCard(){
 const hud=el('hud'),body=el('card-body'),card=CARDS[demo.cardId];
 hud.hidden=!card&&demo.phase!=='error';body.replaceChildren();hud.dataset.kind=demo.cardId;hud.dataset.tone=card&&'tone' in card?String(card.tone):'';hud.dataset.decline=String(demo.phase==='request');
 if(demo.phase==='error'){
  el('card-status').textContent='Action incomplete';el('card-title').textContent='Check before retrying';
  lines(body,[demo.error,...(demo.savedObservation?['Blood pressure saved. Medication note still pending.']:['Nothing saved.'])]);el('card-hint').textContent='Restart if patient binding or capture has expired.';return;
 }
 if(!card)return;
 el('card-status').textContent=card.status;el('card-title').textContent=card.title;el('card-hint').textContent=card.hint;
 if(demo.phase==='receipt'&&demo.requestResponse==='accepted'){el('card-title').textContent='Request accepted';}
 if(demo.phase==='reading'){
  const n=document.createElement('div');n.className='bp-number';n.innerHTML='145/95<small>mmHg</small>';body.append(n);
  const pulse=document.createElement('p');pulse.className='pulse';pulse.textContent='Pulse 88 bpm';body.append(pulse);
  const patient=document.createElement('p');patient.className='card-patient';patient.textContent='Mark Pokidko · Room 7';body.append(patient);
 }else if(demo.phase==='transcribing'||demo.phase==='review'){
  const reading=document.createElement('div');reading.className='reading-line';reading.textContent='145/95 mmHg · Pulse 88 bpm';body.append(reading);
  const note=document.createElement('div');note.className='note-words';note.setAttribute('aria-label',demo.phase==='review'?NOTE:'Medication note being dictated');
  for(const line of card.lines){const row=document.createElement('span');row.className='note-line';for(const word of line.split(' ')){const span=document.createElement('span');span.className='note-word'+(demo.phase==='review'?' revealed':'');span.textContent=word+' ';span.setAttribute('aria-hidden','true');row.append(span)}note.append(row)}
  body.append(note);
 }else if(demo.phase==='handover'){
  const briefing=demo.snap.emergencies.find(e=>e.id===demo.emergencyId)?.isbar;
  if(!briefing){lines(body,['Handover unavailable.']);return;}
  const assessment=(briefing.assessment.match(/[^.]+(?:\.|$)/g)??[]).map(sentence=>sentence.trim());
  lines(body,[`I · ${briefing.identify}`,`S · ${briefing.situation}`,`B · ${briefing.background}`,`A · ${assessment[0]??briefing.assessment}`,...assessment.slice(1),`R · ${briefing.recommendation}`]);
 }else lines(body,card.lines);
}
function render(){
 const phase=demo.phase;el('app').dataset.phase=phase;
 showCard();el('opening').hidden=!['ready','intro'].includes(phase);el('opening').classList.toggle('leaving',phase==='intro');
 el('ending').hidden=phase!=='complete';el('veil').style.opacity=['outro','complete'].includes(phase)?'1':'0';
 el('chapter-number').textContent=String(demo.chapter).padStart(2,'0');el('chapter-name').textContent=titles[demo.chapter-1];
 el('instruction').textContent=instructions[phase];el('scene-caption').textContent=captions[phase]??'';
 el('input-help').textContent=demo.leftLabel||demo.rightLabel?'Hold an arrow or button. Release early to cancel.':demo.primaryLabel?'Press Space or tap the button.':'The experience pauses at your next decision.';
 el('location').textContent=['handover','outro','complete'].includes(phase)?'Ward 3 · To Room 8':'Ward 3 · Room 7';
 el<HTMLButtonElement>('primary').hidden=!demo.primaryLabel||['ready','complete'].includes(phase);
 el<HTMLButtonElement>('primary').textContent=demo.primaryLabel;
 el<HTMLButtonElement>('primary').disabled=phase==='handover'&&demo.elapsed<11000;
 for(const direction of ['left','right'] as const){const label=direction==='left'?demo.leftLabel:demo.rightLabel;const button=el<HTMLButtonElement>('tilt-'+direction);button.hidden=!label;button.setAttribute('aria-label','Hold '+direction+' to '+label.toLowerCase());el(direction+'-label').textContent=label;button.classList.toggle('decline',phase==='request'&&direction==='left');}
 el('waiting').hidden=!!demo.primaryLabel||!!demo.leftLabel||!!demo.rightLabel;el('waiting-text').textContent=waitLabels[phase]||'Continue when ready';
 document.querySelectorAll('.chapter-track i').forEach((n,i)=>{n.classList.toggle('done',i<demo.chapter-1);n.classList.toggle('current',i===demo.chapter-1)});
 el('end-result').textContent=`${demo.snap.observations.length+demo.snap.notes.length} reviewed records saved · Ben notified · Emergency accepted`;
 el('announcement').textContent=cardAnnouncement();
}
function cardAnnouncement(){return demo.phase==='transcribing'?'The nurse is dictating a medication note.':(el('hud').hidden?'':el('card-title').textContent+'. ')+instructions[demo.phase];}
function cancelInput(){tilt.cancel();activePointer=null;activeKey='';document.querySelectorAll('.tilt').forEach(n=>n.classList.remove('active'));}
function restart(){cancelInput();ward?.reset();elapsedTime=0;demo.restart();}
function isPaused(){return document.hidden||!!document.querySelector('dialog[open]');}
function runPrimary(){if(!loaded||isPaused())return;cancelInput();tilt.neutral=300;if(demo.phase==='complete'){ward?.reset();elapsedTime=0;}demo.primary();}
function startTilt(direction:Direction){
 if(!loaded||isPaused()||!(direction==='left'?demo.leftLabel:demo.rightLabel))return;
 tilt.press(direction,demo.prompt);
 if(tilt.direction===direction)el('tilt-'+direction).classList.add('active');
}
function releaseTilt(){tilt.release();document.querySelectorAll('.tilt').forEach(n=>n.classList.remove('active'));}
for(const direction of ['left','right'] as const){
 const button=el<HTMLButtonElement>('tilt-'+direction);
 button.addEventListener('pointerdown',e=>{if(e.button!==0||activePointer!==null)return;e.preventDefault();activePointer=e.pointerId;button.setPointerCapture(e.pointerId);startTilt(direction);});
 button.addEventListener('pointerup',e=>{if(e.pointerId===activePointer){activePointer=null;releaseTilt();}});
 button.addEventListener('pointercancel',cancelInput);
 button.addEventListener('lostpointercapture',()=>{if(activePointer!==null)cancelInput();});
 button.addEventListener('pointermove',e=>{if(activePointer!==e.pointerId)return;const b=button.getBoundingClientRect();if(e.clientX<b.left-20||e.clientX>b.right+20||e.clientY<b.top-20||e.clientY>b.bottom+20)cancelInput();});
 // Enter on the focused button supports keyboard users without arrow keys.
 button.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.repeat){e.preventDefault();startTilt(direction);}});
 button.addEventListener('keyup',e=>{if(e.key==='Enter'){e.preventDefault();releaseTilt();}});
 button.addEventListener('contextmenu',e=>e.preventDefault());
}
document.addEventListener('keydown',e=>{
 if(isPaused()||e.altKey||e.metaKey||e.ctrlKey)return;
 if(e.key==='ArrowLeft'||e.key==='ArrowRight'){
  e.preventDefault();if(e.repeat)return;if(activeKey&&activeKey!==e.key){cancelInput();return;}activeKey=e.key;startTilt(e.key==='ArrowLeft'?'left':'right');
 }else if(e.code==='Space'){e.preventDefault();if(!e.repeat)runPrimary();}
 else if(e.key==='Escape'){cancelInput();if(demo.leftLabel)demo.tilt('left');}
});
document.addEventListener('keyup',e=>{if(e.key===activeKey){activeKey='';releaseTilt();}});
window.addEventListener('blur',cancelInput);document.addEventListener('visibilitychange',()=>{cancelInput();lastTime=performance.now();});
el('begin').onclick=runPrimary;el('primary').onclick=runPrimary;el('replay').onclick=runPrimary;el('restart').onclick=restart;el('reload').onclick=()=>location.reload();
el('about-button').onclick=()=>{cancelInput();el<HTMLDialogElement>('about').showModal();};
document.querySelectorAll<HTMLButtonElement>('[data-close]').forEach(b=>b.onclick=()=>b.closest('dialog')!.close());
const motion=el<HTMLInputElement>('reduce-motion');motion.checked=reduced;document.body.classList.toggle('reduced',reduced);motion.onchange=()=>{reduced=motion.checked;document.body.classList.toggle('reduced',reduced);};
matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change',e=>{reduced=e.matches;motion.checked=reduced;document.body.classList.toggle('reduced',reduced);});
el('records-button').onclick=()=>{
 const body=el('record-content');body.replaceChildren();
 function row(title:string,text:string,detail:string){const section=document.createElement('section');section.className='record-row';const h=document.createElement('h3');h.textContent=title;const p=document.createElement('p');p.textContent=text;const s=document.createElement('small');s.textContent=detail;section.append(h,p,s);body.append(section);}
 row('Patient binding',demo.patient?.name+' · Room '+demo.patient?.room,'Confirmed wristband · '+demo.snap.context?.patientId+' / '+demo.snap.context?.encounterId);
 for(const r of demo.snap.observations)row('Blood pressure saved',`${r.values.systolic}/${r.values.diastolic} mmHg · Pulse ${r.values.pulse} bpm`,`Reviewed by Jooyeon Lee · ${new Date(r.savedAt).toLocaleTimeString()} · Simulated camera`);
 for(const r of demo.snap.notes)row('Medication note saved',r.text,`Authored and reviewed by Jooyeon Lee · ${new Date(r.savedAt).toLocaleTimeString()}`);
 row('Team request',`Room 12 · ${demo.ward.snapshot('ben').tasks.find(t=>t.id===demo.requestId)?.status}`,"Ben’s local receipt");
 row('Emergency response',`Room 8 · ${demo.snap.emergencies.find(e=>e.id===demo.emergencyId)?.response}`,'Staff-authored ISBAR · scoped to this response');
 el<HTMLDialogElement>('records').showModal();
};
demo.onChange=render;
function animation(now:number){
 requestAnimationFrame(animation);
 if(now-frameTime<30)return;const delta=Math.min(100,now-(lastTime||now));lastTime=now;frameTime=now;
 if(!ward||isPaused())return;
 elapsedTime+=delta;
 demo.tick(delta);tilt.tick(delta,demo.prompt);
 const marker=ward.render(elapsedTime/1000,shotFor(),demo.phase,demo.elapsed,reduced);
 const reticle=el('reticle');reticle.style.left=marker.x+'%';reticle.style.top=marker.y+'%';reticle.classList.toggle('visible',marker.visible&&['scan-ready','scanning'].includes(demo.phase));reticle.classList.toggle('scanning',demo.phase==='scanning');
 for(const direction of ['left','right'] as const)el('rail-'+direction).style.transform=`scaleY(${(tilt.prompt===demo.prompt&&tilt.lastDirection===direction)?tilt.progress:0})`;
 if(demo.phase==='transcribing'){
  const words=[...document.querySelectorAll<HTMLElement>('.note-word')];const fraction=Math.max(0,(demo.elapsed-1100)/14900);const count=Math.floor(fraction*words.length);
  words.forEach((word,i)=>word.classList.toggle('revealed',i<count));
 }
 if(demo.phase==='handover')el<HTMLButtonElement>('primary').disabled=demo.elapsed<11000;
}
async function load(){
 try{
  ward=await createWard(el<HTMLCanvasElement>('world'),progress=>{el('load-percent').textContent=Math.round(progress*100)+'%';});
  loaded=true;el<HTMLButtonElement>('begin').disabled=false;el('begin').textContent='Begin experience →';el('loading').textContent='Ready when you are';
 }catch(error){console.error('Lumi 3D load failed',error);el('load-error').hidden=false;el('load-error-text').textContent='Please check your connection and enable WebGL / hardware acceleration, then reload. The demo does not need a camera or microphone.';}
}
el('world').addEventListener('webglcontextlost',e=>{e.preventDefault();cancelInput();loaded=false;el('load-error').hidden=false;el('load-error-text').textContent='The browser paused its 3D graphics. Reload to restart the local demo.';});
render();requestAnimationFrame(animation);void load();
