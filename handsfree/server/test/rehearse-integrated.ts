/** Official DAT mock inputs, not physical speech/optics evidence. */
import {WebSocket} from 'ws';import {once} from 'node:events';import assert from 'node:assert/strict';
let state:any,own:any,peer:any;
const pause=(ms:number)=>new Promise(r=>setTimeout(r,ms));
async function connect(actor:string,role:string,on:(m:any)=>void){const w=new WebSocket(process.env.HF_URL||'ws://127.0.0.1:8792');w.on('message',d=>on(JSON.parse(d.toString())));await once(w,'open');w.send(JSON.stringify({type:'auth',token:actor+'-demo',role}));return w;}
const control=await connect('aino','control',m=>{if(m.type==='sim.state')state=m;if(m.type==='snapshot')own=m;if(m.type==='error')console.error(m)});
const ben=await connect('ben','responder',m=>{if(m.type==='snapshot')peer=m});
const input=(input:any)=>control.send(JSON.stringify({type:'sim.input',input}));
async function until(f:()=>boolean,label:string,ms=10000){const end=Date.now()+ms;while(!f()){if(Date.now()>end)throw Error(label+': '+JSON.stringify(state));await pause(40);}}
const rendered=()=>state?.motion?.eligible&&state?.motion?.rendered;
async function title(t:string){await until(()=>rendered()&&state.card.title===t,t)}
async function heard(text:string,t?:string,final=true,confidence=.95){await until(()=>state?.sdkReady&&state.sdkListening,'speech ready');input({kind:'sdk-transcript',text:'',final:false,confidence:0});await pause(60);input({kind:'sdk-transcript',text,final,confidence});if(t)await title(t);else await pause(1100);}
async function tilt(side:string,t?:string){await until(rendered,'rendered for tilt');input({kind:'gesture',value:side+'-tilt'});if(t)await title(t);else await pause(1300);}
async function wake(){await heard('Hey Lumi','Listening',false,.8);}
try{
 await until(()=>rendered()&&own&&peer,'startup');input({kind:'sdk-voice',active:true});await until(()=>state?.sdkReady&&state.sdkListening,'SDK start');
 await wake();await pause(22000);assert.equal(state.card.title,'Listening');await tilt('left','Display hidden');await wake();
 await heard('Message Ben',undefined,false,.82);await title('Message Ben');await pause(31000);assert.equal(state.card.title,'Message Ben');
 await tilt('left','Display hidden');await wake();await heard('Send Ben a message','Message Ben');
 await heard('Please bring the belt to room twelve.',undefined,false,.82);input({kind:'sdk-transcript',text:'',final:false,confidence:0});await until(()=>rendered()&&state.card.phase==='review','message review');
 const count=own.messages.length;await heard('Send message',undefined,false,1);assert.equal(own.messages.length,count);await tilt('right','Message saved and sent');assert.equal(own.messages.length,count+1);
 console.log('PASS wake, persistent Listening/dictation, left tilt cancellation, partial draft and right-tilt send');
 await tilt('left','Display hidden');await wake();await heard('Show messages','Messages');await tilt('right');assert.equal(state.card.phase,'message');await tilt('left','Messages');await tilt('left','Display hidden');
 await wake();await heard('Show patients','Assigned patients');await tilt('right','Confirm patient');await tilt('right');await until(()=>rendered()&&state.card.title.startsWith('Patient overview'),'patient overview');
 await heard('Show patient details');await until(()=>state.card.title.startsWith('Patient overview'),'overview');await heard('Next');assert.ok(state.card.title.includes('2/'));await tilt('left');await tilt('left');
 await wake();await heard('Scan patient');await title('Confirm patient');await tilt('right');await until(()=>rendered()&&state.card.title.startsWith('Patient overview'),'patient overview');
 await heard('Capture reading');await title('Review reading');await tilt('right','Saved · synthetic record');const observation=own.observations.at(-1);assert.ok(observation);
 await heard('Share reading with Ben');await until(()=>state.card.phase==='review'&&rendered(),'share review');await tilt('right');await until(()=>peer.tasks.some((t:any)=>t.attachment?.id===observation.id),'recipient attachment');
 console.log('PASS navigation, QR pixels, DAT camera capture, save and exact recipient attachment');
 await tilt('left');await wake();await heard('Save to patient records that I gave 400mg of ibuprofen at 12:00.');await until(()=>rendered()&&state.card.phase==='review','note review');await tilt('right');await until(()=>own.notes?.some((n:any)=>n.text.includes('400mg')),'saved note');
 await tilt('left');await wake();await heard('Show latest note');await until(()=>state.card.title.startsWith('Latest permitted note'),'note details');console.log('PASS manual note and permitted details');
 input({kind:'device-lost'});await until(()=>!state.glassesVoice,'loss stops mic');input({kind:'device-ready'});await until(rendered,'restored');assert.equal(state.glassesVoice,false);console.log('PASS disconnect and explicit microphone restart');
}finally{input({kind:'sdk-voice',active:false});control.close();ben.close();}
