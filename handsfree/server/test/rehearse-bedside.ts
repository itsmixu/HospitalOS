/** Requires the installed, running official DAT preview. Injects speech, never ward save commands. */
import {WebSocket} from 'ws';import {once} from 'node:events';import assert from 'node:assert/strict';import {randomUUID} from 'node:crypto';
const endpoint=process.env.HF_URL||'ws://127.0.0.1:8790';
let state:any;let own:any;let other:any;let outsider:any;let stage='connecting';
async function connect(actor:string,role:string,onMessage:(m:any)=>void){const ws=new WebSocket(endpoint);ws.on('message',raw=>onMessage(JSON.parse(raw.toString())));await once(ws,'open');ws.send(JSON.stringify({type:'auth',token:`${actor}-demo`,role}));return ws;}
const control=await connect('aino','control',m=>{if(m.type==='sim.state')state=m;if(m.type==='snapshot')own=m;if(m.type==='error')throw Error(m.reason)});
const ben=await connect('ben','responder',m=>{if(m.type==='snapshot')other=m});
const sara=await connect('sara','responder',m=>{if(m.type==='snapshot')outsider=m});
function input(input:any){control.send(JSON.stringify({type:'sim.input',input}));}
async function until(predicate:()=>boolean,ms=8000){const end=Date.now()+ms;while(!predicate()){if(Date.now()>end)throw Error(`Timeout: ${stage}; current phase ${state?.card?.phase}, title ${state?.card?.title}`);await new Promise(r=>setTimeout(r,25));}}
async function speak(text:string){input({kind:'transcript',text});await new Promise(r=>setTimeout(r,120));}
async function review(title:string){stage=title;await until(()=>state?.card?.title===title && state.renderedPromptId===state.card.promptId);console.log(`PASS visible review: ${title}`);}
try {
 await until(()=>!!state && !!own && !!other && !!outsider);const before=own.observations.length;
 input({kind:'camera',scenario:'clear',seed:Number(process.env.CAMERA_SEED||42)});
 input({kind:'wake'});await until(()=>state?.card?.microphone);await speak('select patient demo-a');await review('Confirm patient');await speak('confirm patient');
 stage='patient confirmed';await until(()=>state?.card?.title==='Patient confirmed');await speak('capture reading');await review('Review reading');assert.equal(own.observations.length,before);
 const original=state.card.body;const prompt=state.card.promptId;
 // An unrelated incoming task must not displace the observation or capture its confirmation.
 ben.send(JSON.stringify({id:randomUUID(),type:'request.create',to:'aino',room:'7',text:'bring transfer belt'}));
 await new Promise(r=>setTimeout(r,180));assert.equal(state.card.promptId,prompt);assert.equal(state.card.body,original);
 const correction=/Pulse 77 bpm/.test(original)?78:77;
 await speak(`correct pulse ${correction}`);stage='corrected prompt displayed';await until(()=>state?.card?.title==='Review reading' && state.card.promptId!==prompt && state.renderedPromptId===state.card.promptId);await review('Review reading');assert.notEqual(state.card.promptId,prompt);assert.ok(state.card.body.includes(`Pulse ${correction} bpm`));assert.match(state.card.body,/Manually corrected/);
 if(process.env.REVIEW_PAUSE==='1')await new Promise(r=>setTimeout(r,3500));
 await speak('save reading');await speak('save reading');stage='save acknowledged';await until(()=>state?.card?.title==='Saved · synthetic record' && own.observations.length===before+1);
 const saved=own.observations.at(-1);assert.equal(saved.values.pulse,correction);assert.equal(other.observations.length,0);assert.equal(outsider.observations.length,0);console.log('PASS explicit save, duplicate confirmation, private record');
 if(process.env.INSPECT_SAVED==='1'){console.log('Saved card ready for inspection; press Enter to continue.');await once(process.stdin,'data');input({kind:'wake'});await until(()=>state?.card?.microphone);}
 await speak('share reading with Ben');await review('Send to Ben?');await speak('send attachment');stage='Ben received attachment';await until(()=>other.tasks.some((t:any)=>t.attachment?.id===saved.id));
 const shared=other.tasks.find((t:any)=>t.attachment?.id===saved.id);assert.deepEqual(shared.attachment.values,saved.values);assert.equal(shared.attachment.capturedAt,saved.capturedAt);assert.equal(outsider.tasks.some((t:any)=>t.attachment?.id===saved.id),false);
 ben.send(JSON.stringify({id:randomUUID(),type:'request.accept',taskId:shared.id,revision:shared.revision}));await until(()=>other.tasks.find((t:any)=>t.id===shared.id)?.status==='accepted' && state?.card?.title==='Ben is coming');
 console.log('PASS separate sharing, exact payload, participant filtering, Ben acceptance');
 console.log('REHEARSAL PASSED: injected speech / injected camera; official DAT display; not physical glasses validation');
 if(process.env.HOLD==='1'){console.log('Holding responder connections for inspection; press Enter to close.');await once(process.stdin,'data');}
}finally{control.terminate();ben.terminate();sara.terminate();process.stdin.pause();}
