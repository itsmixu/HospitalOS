import {slides,duration,positionAt,startOf} from './story';
import {createCinematic} from './cinematic';
const $=<T extends HTMLElement>(id:string)=>document.getElementById(id) as T;
const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
let time=0,playing=false,selected=-1,last=performance.now(),lastDraw=0;
let world:ReturnType<typeof createCinematic>|null=null;
try{world=createCinematic($<HTMLCanvasElement>('scene'))}catch(e){$('fallback').hidden=false;console.error('3D initialization failed',e)}
const slider=$<HTMLInputElement>('timeline');slider.max=String(duration);
const format=(t:number)=>`${Math.floor(t/60)}:${String(Math.floor(t%60)).padStart(2,'0')}`;
$('chapters').innerHTML=slides.map((s,i)=>`<button aria-label="Scene ${i+1}: ${esc(s.title)}" title="${esc(s.title)}" data-scene="${i}" style="flex:${s.seconds}"></button>`).join('');
$('script').innerHTML=slides.map((s,i)=>`<button class="script-scene" data-scene="${i}"><strong>${String(i+1).padStart(2,'0')} · ${esc(s.title)} <small>${format(startOf(i))}</small></strong><span>${esc(s.note)}</span></button>`).join('');
function setPlaying(value:boolean){playing=value;$('play').textContent=value?'Ⅱ':'▶';$('play').setAttribute('aria-label',value?'Pause guided demo':'Play guided demo');last=performance.now();}
function seek(value:number){time=Math.max(0,Math.min(duration,value));selected=-1;render();}
function next(){setPlaying(false);seek(startOf(Math.min(slides.length-1,positionAt(time).index+1)))}
function previous(){setPlaying(false);seek(startOf(Math.max(0,positionAt(time).index-1)))}
function toggle(){if(time>=duration)seek(0);setPlaying(!playing)}
function render(){const {index,progress}=positionAt(time);const s=slides[index];if(selected!==index){
 selected=index;$('chapter').textContent=s.chapter;$('scene-title').textContent=s.title;$('scene-number').textContent=`${String(index+1).padStart(2,'0')} / ${slides.length}`;
 $('location').textContent=['exit','corridor'].includes(s.shot)?'TO ROOM 8':'ROOM 7';
 $('display').classList.toggle('off',!s.heading);$('card').className=s.tone||'';
 $('card').innerHTML=s.heading?`<p class="status uit-text-meta1"><span class="status-dot"></span>${esc(s.status||'Lumi')}</p><h2 class="uit-text-heading1">${esc(s.heading)}</h2>${(s.lines||[]).map(line=>`<p class="body-line uit-text-body2">${esc(line)}</p>`).join('')}${s.hint?`<p class="hint uit-text-meta1">${esc(s.hint)}</p>`:''}`:'';
 $('action-cue').textContent=s.cue||'';$('voice-caption').textContent=s.voice?`“${s.voice}”`:'';$('scan-target').hidden=!s.scan;
 const title=$('title-screen');title.classList.toggle('hidden',!s.mode);title.setAttribute('aria-hidden',String(!s.mode));title.classList.toggle('outro',s.mode==='outro');
 $('title-kicker').textContent=s.mode==='intro'?'LUMI · AT THE BEDSIDE':'Every second matters.';
 $('title-main').innerHTML=s.mode==='intro'?'Eyes on<br>the patient.':'lumi';$('title-sub').textContent=s.mode==='intro'?'Care in focus. Everything else, within view.':'Focusing on the patient.';
 document.querySelectorAll<HTMLElement>('[data-scene]').forEach(b=>{b.classList.toggle('active',Number(b.dataset.scene)===index);b.setAttribute('aria-current',Number(b.dataset.scene)===index?'step':'false')});
 $('previous').toggleAttribute('disabled',index===0);$('next').toggleAttribute('disabled',index===slides.length-1);
 }
 let shotStart=index,shotEnd=index;while(shotStart>0&&slides[shotStart-1].shot===s.shot)shotStart--;while(shotEnd<slides.length-1&&slides[shotEnd+1].shot===s.shot&&!slides[shotEnd+1].mode)shotEnd++;
 const shotDuration=slides.slice(shotStart,shotEnd+1).reduce((sum,x)=>sum+x.seconds,0);const corridorProgress=s.mode==='outro'?1:Math.min(1,(time-startOf(shotStart))/shotDuration);
 const marker=world?.render(time,s.shot,corridorProgress,!!s.ekg,['result','dictate','review','saved'].includes(s.id));if(marker&&s.scan){$('scan-target').style.left=marker.x+'%';$('scan-target').style.top=marker.y+'%'}
 if(s.mode==='outro'){$('fade').style.opacity=String(Math.min(1,progress*7));$('title-screen').style.opacity=String(Math.max(0,Math.min(1,(progress-.13)*6)))}else{$('fade').style.opacity='0';$('title-screen').style.opacity=''}
 slider.value=String(time);$('time').textContent=`${format(time)} / ${format(duration)}`;
}
$('play').onclick=toggle;$('next').onclick=next;$('previous').onclick=previous;$('restart').onclick=()=>{setPlaying(false);seek(0)};
slider.oninput=()=>{setPlaying(false);seek(Number(slider.value))};
document.querySelectorAll<HTMLElement>('[data-scene]').forEach(b=>b.onclick=()=>{setPlaying(false);seek(startOf(Number(b.dataset.scene)));$<HTMLDialogElement>('notes').close()});
$('notes-button').onclick=()=>{setPlaying(false);$<HTMLDialogElement>('notes').showModal()};$('close-notes').onclick=()=>$<HTMLDialogElement>('notes').close();
function cinema(){document.body.classList.toggle('cinema');render()};$('cinema-button').onclick=cinema;
addEventListener('keydown',e=>{if(document.querySelector('dialog[open]')||['INPUT','TEXTAREA'].includes((e.target as HTMLElement).tagName))return;
 if(e.code==='Space'){e.preventDefault();toggle()}else if(e.key==='ArrowRight'){e.preventDefault();next()}else if(e.key==='ArrowLeft'){e.preventDefault();previous()}else if(e.key.toLowerCase()==='c'){cinema()}else if(e.key==='Escape'){document.body.classList.remove('cinema');render()}else if(e.key==='Home'){setPlaying(false);seek(0)}});
addEventListener('resize',()=>render());document.addEventListener('visibilitychange',()=>{if(document.hidden)setPlaying(false)});
function tick(now:number){requestAnimationFrame(tick);const dt=Math.min((now-last)/1000,.2);last=now;if(playing){time=Math.min(duration,time+dt);if(time>=duration)setPlaying(false)}if(now-lastDraw>=(playing?33:250)){render();lastDraw=now}}
render();requestAnimationFrame(tick);
