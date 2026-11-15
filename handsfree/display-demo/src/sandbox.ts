import {GuidedDemo,NOTE,VALUES,ISBAR,CARDS} from '../../web-demo/src/guided-model';
import type {Context} from '../../server/src/observations';
export {TiltInput} from '../../web-demo/src/guided-model';
type View={status:string;title:string;lines:string[];hint:string};
export class Sandbox {
 core=new GuidedDemo();phase='idle';elapsed=0;prompt=0;onChange=()=>{};
 reading:{binding:Context;captureId:string;capturedAt:string}|null=null;
 note:{binding:Context;draftId:string;text:string}|null=null;
 messageId='';requestId='';emergencyId='';receipt='';error='';returnPhase='idle';stack:Array<{phase:string;elapsed:number;messageId:string;requestId:string;emergencyId:string}>=[];
 get snap(){return this.core.snap;}get patient(){return this.core.patient;}
 apply(c:Record<string,unknown>,actor=this.core.actor){return this.core.apply(c,actor);}
 go(p:string){this.phase=p;this.elapsed=0;this.prompt++;this.onChange();}
 guard(fn:()=>void){try{fn();}catch(e){this.error=e instanceof Error?e.message:String(e);this.go('error');}}
 binding(){if(!this.snap.context)throw Error('Scan and confirm a patient first.');return {...this.snap.context};}
 remember(){if(this.phase!=='idle')this.stack.push({phase:this.phase,elapsed:this.elapsed,messageId:this.messageId,requestId:this.requestId,emergencyId:this.emergencyId});if(this.stack.length>20)this.stack.shift();}
 restore(){const p=this.stack.pop();if(!p){this.go('idle');return;}this.messageId=p.messageId;this.requestId=p.requestId;this.emergencyId=p.emergencyId;this.go(p.phase);this.elapsed=p.elapsed;}
 action(action:string,text=''){this.guard(()=>{
 if(!['message','request','emergency'].includes(action))this.stack=[];
 switch(action){
 case 'wake':this.go('home');break;
 case 'scan':this.go('scan');break;
 case 'patient':if(!this.patient)throw Error('Scan and confirm a patient first.');this.go('overview');break;
 case 'capture':this.binding();this.go('capture');break;
 case 'note':this.note={binding:this.binding(),draftId:crypto.randomUUID(),text:(text||NOTE).trim()};if(!this.note.text||this.note.text.length>1000)throw Error('Use a note between 1 and 1000 characters.');this.go('dictation');break;
 case 'draft':this.go(this.note?'review':this.reading?'reading':'home');break;
 case 'message':{this.remember();this.messageId=this.apply({type:'message.send',to:this.core.actor,text:text||'Can you bring the hoist to Room 12 when you are free?'},'ben').messageId!;this.go('message');break;}
 case 'inbox':{const m=this.snap.messages.filter(m=>m.to===this.core.actor&&!m.readAt)[0];if(!m)throw Error('No unread messages.');this.messageId=m.id;this.returnPhase='idle';this.go('message');break;}
 case 'request':{this.remember();this.requestId=this.apply({type:'request.create',to:this.core.actor,room:'12',text:'Assistance requested'},'ben').taskId!;const t=this.core.ward.tasks.find(t=>t.id===this.requestId)!;this.apply({type:'request.delivered',taskId:t.id,revision:t.revision});this.go('request');break;}
 case 'emergency':{this.remember();const active=this.snap.emergencies.find(e=>e.status==='active');if(active){this.emergencyId=active.id;this.go(active.response==='acknowledged'?'isbar':'emergency');break;}this.apply({type:'emergency.create',patientId:'demo-c',encounterId:'enc-c',isbar:ISBAR},'ben');this.emergencyId=this.core.ward.emergencies.records.at(-1)!.id;this.go('emergency');break;}
 case 'alerts':{const e=this.snap.emergencies.find(e=>e.response!=='acknowledged');if(!e)throw Error('No pending emergency alerts.');this.emergencyId=e.id;this.go('emergency');break;}
 case 'reset':this.core=new GuidedDemo();this.reading=null;this.note=null;this.messageId='';this.requestId='';this.emergencyId='';this.returnPhase='idle';this.go('idle');break;
 }
 });}
 tick(ms:number){if(ms<0||!Number.isFinite(ms))return;this.elapsed+=ms;this.guard(()=>{
 if(this.phase==='scan'&&this.elapsed>=1400)this.go('patient-confirm');
 else if(this.phase==='capture'&&this.elapsed>=1400){this.reading={binding:this.binding(),captureId:crypto.randomUUID(),capturedAt:new Date().toISOString()};this.go('reading');}
 else if(this.phase==='dictation'&&this.elapsed>=6000)this.go('review');
 else if(this.phase==='receipt'&&this.elapsed>=2300)this.restore();
 });}
 tilt(direction:'left'|'right',prompt=this.prompt){if(prompt!==this.prompt)return;this.guard(()=>{
 if(direction==='left'){
 if(this.phase==='request')this.respond('decline');
 else if(['message','message-open','emergency','isbar'].includes(this.phase))this.restore();
 else this.go('idle');
 return;
 }
 switch(this.phase){
 case 'patient-confirm':this.apply({type:'patient.confirm',patientId:'demo-a',encounterId:'enc-a'});this.go('overview');break;
 case 'reading':{const p=this.reading;if(!p)throw Error('No pending reading.');this.apply({type:'observation.save',...p.binding,captureId:p.captureId,capturedAt:p.capturedAt,reviewedAt:new Date().toISOString(),profile:'HOS-BP-1/v1',values:VALUES,original:VALUES,units:{pressure:'mmHg',pulse:'bpm'},corrected:[],source:'injected-camera'});this.reading=null;this.stack=[];this.receipt='Blood pressure saved';this.returnPhase='idle';this.go('receipt');break;}
 case 'review':{const n=this.note;if(!n)throw Error('No pending note.');this.apply({type:'note.save',...n.binding,draftId:n.draftId,text:n.text,reviewedAt:new Date().toISOString(),source:'manual-dictation'});this.note=null;this.stack=[];this.receipt='Medication note saved';this.returnPhase='idle';this.go('receipt');break;}
 case 'request':this.respond('accept');break;
 case 'message':this.apply({type:'message.read',messageId:this.messageId});this.go('message-open');break;
 case 'emergency':{const e=this.core.ward.emergencies.records.find(e=>e.id===this.emergencyId)!;this.apply({type:'emergency.acknowledge',emergencyId:e.id,revision:e.revision});this.go('isbar');break;}
 }
 });}
 respond(action:'decline'|'accept'){const t=this.core.ward.tasks.find(t=>t.id===this.requestId)!;this.apply({type:'request.'+action,taskId:t.id,revision:t.revision});this.receipt=action==='decline'?'Request declined':'Request accepted';this.go('receipt');}
 get leftLabel(){return this.phase==='idle'?'':this.phase==='request'?'Decline':this.phase==='message'?'Later':'Back';}
 get rightLabel(){return ({'patient-confirm':'Confirm',reading:'Save reading',review:'Save note',request:'Accept',message:'Read',emergency:'Accept'} as Record<string,string>)[this.phase]||'';}
 get view():View{
 const id=({scan:'scan','patient-confirm':'patient-confirm',overview:'patient-info',capture:'capture',reading:'reading',request:'request',emergency:'code-blue',isbar:'isbar'} as Record<string,string>)[this.phase];
 if(this.phase==='isbar'){const b=this.snap.emergencies.find(e=>e.id===this.emergencyId)?.isbar;return {status:'Accepted · Room 8',title:'ISBAR handover',lines:b?[`I · ${b.identify}`,`S · ${b.situation}`,`B · ${b.background}`,`A · ${b.assessment}`,`R · ${b.recommendation}`]:['Handover unavailable'],hint:'Staff-authored · tilt left to close'};}
 if(id&&CARDS[id]){const c=CARDS[id];return {...c,lines:this.phase==='reading'?['145/95 mmHg','Pulse 88 bpm','Mark Pokidko · Room 7']:c.lines};}
 if(this.phase==='message')return {status:'Incoming message',title:'Message from Ben',lines:['Tilt right to read'],hint:'Tilt left to save for later'};
 if(this.phase==='message-open')return {status:'Ben · message',title:'Team message',lines:[this.snap.messages.find(m=>m.id===this.messageId)?.text||'Message unavailable'],hint:'Tilt left to close'};
 if(this.phase==='dictation'||this.phase==='review')return {status:this.phase==='review'?'Review before saving':'Simulated dictation',title:'Patient note',lines:[this.note?.text||''],hint:this.phase==='review'?'Tilt right to save · left to back':'Draft only · not saved'};
 if(this.phase==='receipt')return {status:'Confirmed',title:this.receipt,lines:this.receipt.startsWith('Request')?['Ben has been notified']:['Mark Pokidko · Room 7'],hint:''};
 if(this.phase==='error')return {status:'Action unavailable',title:'Check before continuing',lines:[this.error],hint:'Tilt left to close'};
 return {status:'Lumi',title:'How can I help?',lines:[this.patient?this.patient.name+' · Room 7':'No patient selected',`${this.snap.messages.filter(m=>m.to===this.core.actor&&!m.readAt).length} unread messages`],hint:'Choose any action from the controls'};
 }
}
