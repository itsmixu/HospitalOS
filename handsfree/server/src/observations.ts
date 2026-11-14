import { randomUUID } from 'node:crypto';
import { z } from 'zod';
export const patients = [
  { id:'demo-a', name:'Demo Patient A', encounterId:'enc-a', room:'12', warning:'Synthetic warning: assistance required for transfers', reported:'Patient reports discomfort when standing.', staff:['aino','ben'] },
  { id:'demo-b', name:'Demo Patient B', encounterId:'enc-b', room:'14', warning:null, reported:'Patient requested water.', staff:['aino'] },
];
const id = z.string().min(1).max(100);
const tuple = z.object({ systolic:z.number().int().min(0).max(999), diastolic:z.number().int().min(0).max(999), pulse:z.number().int().min(0).max(999) }).strict();
const binding = { patientId:id, encounterId:id, contextRevision:z.number().int().positive() };
const units = z.object({ pressure:z.literal('mmHg'), pulse:z.literal('bpm') }).strict();
export const observationSchema = z.discriminatedUnion('type',[
  z.object({id,type:z.literal('context.clear')}).strict(),
  z.object({id,type:z.literal('patient.confirm'),patientId:id,encounterId:id}).strict(),
  z.object({id,type:z.literal('observation.save'),...binding,captureId:id,capturedAt:z.iso.datetime(),reviewedAt:z.iso.datetime(),profile:z.literal('HOS-BP-1/v1'),values:tuple,original:tuple,units,corrected:z.array(z.enum(['systolic','diastolic','pulse'])).max(3),source:z.enum(['injected-camera','dat-camera'])}).strict(),
  z.object({id,type:z.literal('observation.share'),...binding,observationId:id,to:id,text:z.string().trim().min(1).max(120)}).strict(),
]);
export type ObservationCommand = z.infer<typeof observationSchema>;
export type Context = {patientId:string;encounterId:string;contextRevision:number};
export type Observation = Omit<Extract<ObservationCommand,{type:'observation.save'}>,'type'|'id'> & {id:string;reviewer:string;savedAt:string;measurementTime:null;synthetic:true};
/** Volatile synthetic records, not an EHR. No images or clinical logging. */
export class Observations {
  records: Observation[]=[];
  private contexts=new Map<string,Context>();
  private revision=0;
  clear(session:string){this.contexts.delete(session);}
  context(session:string){return this.contexts.get(session);}
  assigned(actor:string,patientId:string){return patients.find(p=>p.id===patientId && p.staff.includes(actor));}
  confirm(actor:string,session:string,patientId:string,encounterId:string):Context|undefined {
    this.clear(session);const p=this.assigned(actor,patientId);if(!p || p.encounterId!==encounterId)return;
    const context={patientId,encounterId,contextRevision:++this.revision};this.contexts.set(session,context);return context;
  }
  valid(actor:string,session:string,c:Context){const a=this.context(session),p=this.assigned(actor,c.patientId);return !!a && !!p && p.encounterId===c.encounterId && a.patientId===c.patientId && a.encounterId===c.encounterId && a.contextRevision===c.contextRevision;}
  save(actor:string,session:string,c:Extract<ObservationCommand,{type:'observation.save'}>):Observation|string {
    if(!this.valid(actor,session,c))return 'Patient context changed; confirm patient again';
    const now=Date.now(), captured=Date.parse(c.capturedAt), reviewed=Date.parse(c.reviewedAt);
    if(captured>reviewed || reviewed>now+5000 || captured<now-10*60*1000)return 'Capture expired or invalid clock';
    const changed=(['systolic','diastolic','pulse'] as const).filter(k=>c.values[k]!==c.original[k]);
    if(new Set(c.corrected).size!==c.corrected.length || changed.length!==c.corrected.length || changed.some(k=>!c.corrected.includes(k)))return 'Correction provenance does not match';
    const {id:_id,type:_type,...payload}=c;
    const old=this.records.find(o=>o.captureId===c.captureId && o.reviewer===actor);
    if(old){const {id,reviewer,savedAt,measurementTime,synthetic,...prior}=old;return JSON.stringify(prior)===JSON.stringify(payload)?old:'Capture already saved with different content';}
    if(this.records.length>=500)return 'Synthetic store capacity reached';
    const record:Observation={...payload,id:randomUUID(),reviewer:actor,savedAt:new Date(now).toISOString(),measurementTime:null,synthetic:true};this.records.push(record);return record;
  }
}
