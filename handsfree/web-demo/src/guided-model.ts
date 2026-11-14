import {HandsFreeWard} from '../../server/src/handsfree-core.ts';
import {Observations, patients, type Context} from '../../server/src/observations.ts';
import video from '../../../prototype/demo-data/lumi-video-v6.json';

import scenario from '../../../prototype/demo-data/lumi-guided-scenario.json';
export const NOTE = scenario.note;
export const VALUES = Object.freeze(scenario.values);
export const QR = scenario.qr;
export const CARDS = Object.fromEntries(video.states.map(card => [card.id, card]));
export const ISBAR = Object.freeze(scenario.isbar);

// This instance-only scenario adapter supplies the film's synthetic cast. The
// real ward still owns assignment, revision, review, deduplication and receipts.
class FilmObservations extends Observations {
  readonly cast = patients.map(p => p.id === scenario.patient.id ? {...p, ...structuredClone(scenario.patient)} : {...p, staff:[...p.staff]});
  override assigned(actor:string, id:string) { return this.cast.find(p => p.id===id && p.staff.includes(actor)); }
}
class FilmWard extends HandsFreeWard {
  override observations = new FilmObservations();
  constructor(){ super(); this.staff=[{...scenario.actor,reachable:true,status:'available'}, {id:'ben',name:'Ben',reachable:true,status:'available'}, {id:'sara',name:'Sara',reachable:true,status:'available'}]; }
  override snapshot(actor:string, session=actor) {
    return {...super.snapshot(actor,session), patients:this.observations.cast.filter(p=>p.staff.includes(actor)).map(({staff,...p})=>p)};
  }
}

export type Phase = 'ready'|'intro'|'approach'|'scan-ready'|'scanning'|'patient-confirm'|'overview'|'procedure'|'request'|'receipt'|'measuring'|'capture-ready'|'capturing'|'reading'|'medication'|'note-ready'|'transcribing'|'note-paused'|'review'|'saved'|'emergency'|'emergency-paused'|'handover'|'outro'|'complete'|'error';
export type Direction = 'left'|'right';
const timed:Partial<Record<Phase,[number,Phase]>> = {
  intro:[2800,'approach'], approach:[3600,'scan-ready'], scanning:[1700,'patient-confirm'],
  procedure:[4400,'request'], receipt:[2200,'measuring'], measuring:[3600,'capture-ready'],
  capturing:[1800,'reading'], medication:[8000,'note-ready'], transcribing:[17200,'review'],
  saved:[4200,'emergency'], outro:[2600,'complete'],
};
export class GuidedDemo {
  ward = new FilmWard(); readonly actor=scenario.actor.id; phase:Phase='ready'; elapsed=0; prompt=0; run=0;
  requestId=''; emergencyId=''; requestResponse=''; error=''; previous:Phase='ready';
  pending:{binding:Context; captureId:string; capturedAt:string; draftId:string; reviewedAt?:string}|null=null;
  savedObservation=''; savedNote=''; medicationGiven=false; pausedFrom:Phase='transcribing';
  onChange=()=>{};
  get snap(){return this.ward.snapshot(this.actor);}
  get patient(){return this.snap.patients.find(p=>p.id===this.snap.context?.patientId);}
  get duration(){return timed[this.phase]?.[0] ?? 0;}
  get chapter(){
    if(['ready','intro','approach','scan-ready','scanning','patient-confirm','overview'].includes(this.phase))return 1;
    if(['procedure','request','receipt','measuring','capture-ready','capturing','reading'].includes(this.phase))return 2;
    if(['medication','note-ready','transcribing','note-paused','review','saved'].includes(this.phase))return 3;
    return 4;
  }
  get leftLabel(){
    if(this.phase==='request')return 'Decline';
    if(['scanning','patient-confirm','overview','capturing','transcribing','review','handover'].includes(this.phase))return 'Back';
    if(this.phase==='emergency')return 'Not now';
    return '';
  }
  get rightLabel(){return ({'patient-confirm':'Confirm patient',request:'Accept',review:'Save records',emergency:'Accept response'} as Partial<Record<Phase,string>>)[this.phase] ?? '';}
  get primaryLabel(){return ({ready:'Begin experience','scan-ready':'Scan wristband',overview:'Begin blood-pressure check','capture-ready':'Capture reading',reading:'Give prescribed medication','note-ready':'“Hey Lumi, add patient note”','note-paused':this.pausedFrom==='review'?'Review draft':'Resume dictation','emergency-paused':'Review emergency',handover:'Continue to Room 8',complete:'Experience again',error:'Try again'} as Partial<Record<Phase,string>>)[this.phase] ?? '';}
  get cardId(){return ({scanning:'scan','patient-confirm':'patient-confirm',overview:'patient-info',request:'request',receipt:'declined',capturing:'capture',reading:'reading',transcribing:'transcription',review:'review',saved:'saved',emergency:'code-blue',handover:'isbar'} as Partial<Record<Phase,string>>)[this.phase] ?? '';}
  apply(command:Record<string,unknown>,actor=this.actor){
    const ack=this.ward.apply(actor,{id:crypto.randomUUID(),...command});
    if(!ack.ok)throw Error(ack.reason || 'Action rejected'); return ack;
  }
  transition(phase:Phase){
    this.phase=phase;this.elapsed=0;this.prompt++;
    if(phase==='request'&&!this.requestId){
      this.requestId=this.apply({type:'request.create',to:this.actor,room:'12',text:'Assistance requested'},'ben').taskId!;
      const task=this.ward.tasks.find(t=>t.id===this.requestId)!;
      this.apply({type:'request.delivered',taskId:task.id,revision:task.revision});
    }
    if(phase==='reading'&&!this.pending){
      if(!this.snap.context)throw Error('Confirm the patient before capturing.');
      this.pending={binding:{...this.snap.context},captureId:crypto.randomUUID(),capturedAt:new Date().toISOString(),draftId:crypto.randomUUID()};
    }
    if(phase==='note-ready')this.medicationGiven=true;
    if(phase==='emergency'&&!this.emergencyId){
      this.apply({type:'emergency.create',patientId:'demo-c',encounterId:'enc-c',isbar:ISBAR},'ben');
      this.emergencyId=this.ward.emergencies.records.at(-1)!.id;
    }
    this.onChange();
  }
  guarded(action:()=>void){try{action();}catch(e){this.previous=this.phase;this.error=e instanceof Error?e.message:String(e);this.transition('error');}}
  tick(ms:number){
    if(ms<0||!Number.isFinite(ms))return;
    this.elapsed+=ms;
    const next=timed[this.phase];
    if(next&&this.elapsed>=next[0])this.guarded(()=>this.transition(next[1]));
  }
  primary(){this.guarded(()=>{
    switch(this.phase){
      case 'ready':this.transition('intro');break;
      case 'scan-ready':this.scan(QR);break;
      case 'overview':this.apply({type:'presence.set',status:'busy'});this.transition('procedure');break;
      case 'capture-ready':this.transition('capturing');break;
      case 'reading':this.transition('medication');break;
      case 'note-ready':this.transition('transcribing');break;
      case 'note-paused':this.transition(this.pausedFrom);break;
      case 'emergency-paused':this.transition('emergency');break;
      case 'handover':if(this.elapsed>=11000)this.transition('outro');break;
      case 'complete':this.restart();this.transition('intro');break;
      case 'error':this.error='';this.transition(this.previous);break;
    }
  });}
  scan(payload:string){
    if(this.phase!=='scan-ready')return;
    if(payload!==QR)throw Error('Wristband is not assigned to this demo nurse.');
    this.apply({type:'context.clear'});this.transition('scanning');
  }
  tilt(direction:Direction, prompt=this.prompt){
    if(prompt!==this.prompt)return;
    this.guarded(()=>{
      if(direction==='right'){
        switch(this.phase){
          case 'patient-confirm':this.apply({type:'patient.confirm',patientId:'demo-a',encounterId:'enc-a'});this.transition('overview');break;
          case 'request':this.respond('accept');break;
          case 'review':this.save();break;
          case 'emergency':{
            const e=this.ward.emergencies.records.find(e=>e.id===this.emergencyId)!;
            this.apply({type:'emergency.acknowledge',emergencyId:e.id,revision:e.revision});this.transition('handover');break;
          }
        }
      }else{
        switch(this.phase){
          case 'request':this.respond('decline');break;
          case 'scanning':case 'patient-confirm':case 'overview':this.apply({type:'context.clear'});this.transition('scan-ready');break;
          case 'capturing':this.transition('capture-ready');break;
          case 'transcribing':case 'review':this.pausedFrom=this.phase;this.transition('note-paused');break;
          case 'emergency':this.transition('emergency-paused');break;
          case 'handover':this.transition('outro');break;
        }
      }
    });
  }
  respond(action:'decline'|'accept'){
    const task=this.ward.tasks.find(t=>t.id===this.requestId)!;
    this.apply({type:action==='decline'?'request.decline':'request.accept',taskId:task.id,revision:task.revision});
    this.requestResponse=action==='decline'?'declined':'accepted';this.transition('receipt');
  }
  save(){
    const p=this.pending;
    if(!p||!this.medicationGiven)throw Error('No complete draft to review.');
    if(!this.ward.observations.valid(this.actor,this.actor,p.binding))throw Error('Patient context changed. Restart to bind the correct patient.');
    p.reviewedAt ??= new Date().toISOString();
    // Two independently acknowledged writes, deliberately not a pretend atomic
    // transaction. Successful IDs survive a retry if the second write fails.
    if(!this.savedObservation)this.savedObservation=this.apply({type:'observation.save',...p.binding,captureId:p.captureId,capturedAt:p.capturedAt,reviewedAt:p.reviewedAt,profile:'HOS-BP-1/v1',values:VALUES,original:VALUES,units:{pressure:'mmHg',pulse:'bpm'},corrected:[],source:'injected-camera'}).observationId!;
    if(!this.savedNote)this.savedNote=this.apply({type:'note.save',...p.binding,draftId:p.draftId,text:NOTE,reviewedAt:p.reviewedAt,source:'manual-dictation'}).noteId!;
    if(!this.savedObservation||!this.savedNote)throw Error('Waiting for both record receipts.');
    this.transition('saved');
  }
  restart(){
    this.ward=new FilmWard();this.pending=null;this.requestId='';this.emergencyId='';this.requestResponse='';this.savedObservation='';this.savedNote='';this.medicationGiven=false;this.error='';this.run++;this.transition('ready');
  }
}

// A held gesture belongs to ONE rendered prompt. Changing cards, focus loss,
// pointer cancellation, opposite input or incomplete release cannot submit.
export class TiltInput {
  direction:Direction|null=null;lastDirection:Direction|null=null;progress=0;latched=false;neutral=300;prompt=-1;
  constructor(private complete:(direction:Direction,prompt:number)=>void){}
  press(direction:Direction,prompt:number){
    if(this.direction || this.latched || this.neutral<300)return;
    this.direction=direction;this.lastDirection=direction;this.prompt=prompt;this.neutral=0;
  }
  release(){this.direction=null;this.neutral=0;}
  cancel(){this.direction=null;this.progress=0;this.latched=false;this.neutral=0;this.prompt=-1;}
  tick(ms:number,prompt:number){
    if(this.direction&&!this.latched){
      if(this.prompt!==prompt){this.cancel();return;}
      this.progress=Math.min(1,this.progress+ms/850);
      if(this.progress>=1){this.latched=true;this.complete(this.direction,this.prompt);}
    }else if(!this.direction){
      this.progress=Math.max(0,this.progress-ms/250);this.neutral+=ms;
      if(this.neutral>=300&&this.progress===0)this.latched=false;
    }
  }
}
