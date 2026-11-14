/** Fault injection against the running Android controller; does not write observations directly. */
import {WebSocket} from 'ws';import {once} from 'node:events';import assert from 'node:assert/strict';
const ws=new WebSocket('ws://127.0.0.1:8790');let state:any;let records=0;let stage='connect';
ws.on('message',raw=>{const m=JSON.parse(raw.toString());if(m.type==='sim.state')state=m;if(m.type==='snapshot')records=m.observations.length});await once(ws,'open');ws.send(JSON.stringify({type:'auth',token:'aino-demo',role:'control'}));
const delay=(ms:number)=>new Promise(r=>setTimeout(r,ms));const input=(input:any)=>ws.send(JSON.stringify({type:'sim.input',input}));
async function until(p:()=>boolean,ms=8000){const end=Date.now()+ms;while(!p()){if(Date.now()>end)throw Error(`${stage}: ${state?.card?.title}`);await delay(25)}}
async function say(text:string){input({kind:'transcript',text});await delay(120)}
async function identify(){input({kind:'wake'});await until(()=>state?.card?.microphone);const previous=state?.card?.promptId;await say('select patient demo-a');await until(()=>state.card.title==='Confirm patient'&&state.card.promptId!==previous&&state.card.body.includes('demo-a')&&state.renderedPromptId===state.card.promptId);await say('confirm patient');await until(()=>state.card.title==='Patient confirmed')}
try{
 await until(()=>!!state);const initial=records;
 for(const scenario of ['blur','glare','crop','missing-label','ambiguous','denied','unavailable']){
  stage=scenario;await identify();input({kind:'camera',scenario,seed:42});await say('capture reading');await until(()=>['No reliable reading','Camera stopped'].includes(state.card.title));assert.equal(records,initial);console.log(`PASS ${scenario}: rejected, no observation`);
 }
 stage='patient switch';await identify();input({kind:'camera',scenario:'clear',seed:331});await say('capture reading');await say('select patient demo-b');await delay(800);assert.equal(state.card.title,'Confirm patient');assert.match(state.card.body,/demo-b/);assert.equal(records,initial);await say('cancel');await until(()=>state.card.title==='Stopped'&&!state.card.microphone);
 stage='cancel review';await identify();await say('capture reading');await until(()=>state.card.title==='Review reading');await say('cancel');await say('save reading');assert.equal(records,initial);console.log('PASS patient switch, stale result, cancellation');
 stage='retry';await identify();await say('capture reading');await until(()=>state.card.title==='Review reading');const first=state.card.promptId;await say('retry');await until(()=>state.card.title==='Review reading'&&state.card.promptId!==first);assert.equal(records,initial);await say('cancel');console.log('PASS retry creates fresh review without saving');
 console.log('FAULT REHEARSAL PASSED · simulated speech and camera only');
}finally{ws.terminate()}
