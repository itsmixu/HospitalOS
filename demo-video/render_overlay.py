#!/usr/bin/env python3
"""Lumi POV composite. Source footage is read-only; all timing is in seconds.
Uses locally installed Arial to approximate Meta's Noto Sans display typography.
No live product events are inferred from footage: this is a scripted composite.
"""
from pathlib import Path
import argparse, json, math, subprocess, sys
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT=Path(__file__).resolve().parent
W,H,FPS=1488,1968,30
X,RIGHT,BOTTOM=520,1430,1110
CW=RIGHT-X
FONTDIR=Path('/System/Library/Fonts/Supplemental')
REG=str(FONTDIR/'Arial.ttf'); BOLD=str(FONTDIR/'Arial Bold.ttf')
def font(size,bold=False): return ImageFont.truetype(BOLD if bold else REG,size)
WHITE=(250,252,255,255); SECONDARY=(210,218,227,245); MUTED=(192,205,217,245)
BLUE=(174,216,247,255); GREEN=(185,226,205,255); RED=(255,183,178,255)
SOURCE_DURATION=79.146667
TRANSCRIPTION={}
TILTS=[
 dict(state='patient-confirm',start=6.0,end=7.0,direction='right',action='confirm'),
 dict(state='request',start=22.0,end=23.0,direction='left',action='decline'),
 dict(state='review',start=66.0,end=67.0,direction='right',action='save'),
 dict(state='code-blue',start=73.0,end=74.0,direction='right',action='accept'),
]
OUTRO=dict(fade_start=77.35,fade_end=79.1,title_start=79.5,title_full=80.3,
           title_fade=85.3,end=86.0,lines=['Your hands save lives.','Keep them free.'])
# Fixed, head-locked HUD. Only the short QR target follows the visible wristband.
STATES=[
 dict(id='scan',start=3,end=4.1,status='Camera active',title='Scanning wristband',lines=['Hold steady'],hint='Looking for patient identity',mode='scan'),
 dict(id='patient-confirm',start=4.1,end=7,status='Patient identified',title='Confirm patient',lines=['Mark Pokidko','Room 7 · Demo patient'],hint='Tilt right to confirm · left to back'),
 dict(id='patient-info',start=7,end=11,status='Patient overview · 1 / 2',title='Mark Pokidko',lines=['Room 7','Reason · high blood pressure','Allergies · none recorded','Prescription · furosemide','40 mg · oral'],hint='Assigned nurse · Jooyeon Lee'),
 dict(id='patient-care',start=11,end=15,status='Patient overview · 2 / 2',title='Current care',lines=['Blood-pressure check','Awaiting a confirmed reading','Medication administration','Not yet recorded'],hint='Mark Pokidko · Room 7'),
 dict(id='request',start=21.0,end=23,status='Team request · Ben',title='Nurse needed',lines=['Room 12','Assistance requested'],hint='Tilt right to accept · left to decline'),
 dict(id='declined',start=23,end=24.6,status='Response confirmed',title='Request declined',lines=['Ben has been notified'],hint='',tone='success'),
 dict(id='capture',start=30,end=31.5,status='Camera active',title='Reading monitor',lines=['Mark Pokidko · Room 7','Hold steady'],hint='Capturing blood pressure',mode='scan'),
 dict(id='reading',start=31.5,end=35.5,status='Reading captured · not saved',title='Blood pressure',lines=[],hint='Pending your confirmation',mode='reading'),
 dict(id='listen',start=44,end=47,status='Glasses mic · listening',title='Add patient note',lines=['Mark Pokidko · Room 7'],hint='Draft only · not saved',mode='listening'),
 dict(id='dictation-1',start=47,end=51,status='Glasses mic · listening',title='Medication note',lines=['40 mg of oral furosemide'],hint='Draft only · not saved',mode='dictating'),
 dict(id='dictation-2',start=51,end=56,status='Glasses mic · listening',title='Medication note',lines=['40 mg of oral furosemide','given to Mark Pokidko'],hint='Draft only · not saved',mode='dictating'),
 dict(id='dictation-3',start=56,end=60,status='Glasses mic · listening',title='Medication note',lines=['40 mg of oral furosemide','given to Mark Pokidko','by nurse Jooyeon Lee'],hint='Draft only · not saved',mode='dictating'),
 dict(id='dictation-4',start=60,end=64,status='Glasses mic · listening',title='Medication note',lines=['40 mg of oral furosemide','given to Mark Pokidko','by nurse Jooyeon Lee at 15:30','for high blood pressure.'],hint='Draft only · not saved',mode='dictating'),
 dict(id='review',start=64,end=67,status='Review before saving',title='Confirm documentation',lines=['40 mg of oral furosemide','given to Mark Pokidko','by nurse Jooyeon Lee at 15:30','for high blood pressure.'],hint='Tilt right to save · left to back',mode='review'),
 dict(id='saved',start=67,end=69.4,status='Saved · synthetic record',title='Documentation saved',lines=['Blood pressure · 145/95 mmHg','Pulse · 88 bpm','Medication note saved','Jooyeon Lee · 15:30'],hint='Mark Pokidko · Room 7',tone='success'),
 dict(id='code-blue',start=70,end=74,status='Emergency · ward alert',title='CODE BLUE',lines=['Room 8','Cardiac arrest','Response requested'],hint='Tilt right to accept',tone='urgent'),
 dict(id='isbar',start=74,end=79.146667,status='Accepted · Room 8',title='ISBAR handover',lines=['I · 65-year-old male','S · Cardiac arrest. CPR ongoing.','B · ASA medication allergy','A · One defibrillation delivered.','IV access established.','No adrenaline given yet.','R · Join the response team.'],hint='Latest report from the response team',tone='urgent')
]

# The saved manifest is editable; subsequent renders use its states and placement.
manifest=ROOT.parent/'prototype'/'demo-data'/'lumi-video-v6.json'
if manifest.exists():
 saved=json.loads(manifest.read_text())
 STATES=saved.get('states',STATES)
 TILTS=saved.get('tilts',TILTS);OUTRO=saved.get('outro',OUTRO)
 TRANSCRIPTION=saved.get('transcription',{})
 X=saved.get('hud',{}).get('left',X);RIGHT=saved.get('hud',{}).get('right',RIGHT);BOTTOM=saved.get('hud',{}).get('bottom',BOTTOM);CW=RIGHT-X

def wrapped(text,size,maxw,bold=False):
 f=font(size,bold);out=[];line=''
 for word in text.split():
  candidate=(line+' '+word).strip()
  if f.getlength(candidate)>maxw and line:out.append(line);line=word
  else:line=candidate
 if line:out.append(line)
 return out

def card(state):
 pad=38;maxw=CW-pad*2
 status=state.get('status',''); title=wrapped(state['title'],58,maxw,True)
 rows=[]
 for line in state.get('lines',[]):rows.extend(wrapped(line,43,maxw))
 hints=wrapped(state.get('hint',''),32,maxw)
 mode=state.get('mode','');extra=146 if mode=='reading' else 104 if mode in ('review','streaming') else 45 if mode in ('listening','dictating','scan') else 0
 height=pad+40+22+len(title)*69+14+len(rows)*57+extra+(25+len(hints)*43 if hints else 0)+pad
 # No scrolling or truncated text in a recorded video.
 assert height<=990,(state['id'],height)
 canvas=Image.new('RGBA',(CW,height),(0,0,0,0));d=ImageDraw.Draw(canvas)
 # The dark optical dimmer and low-contrast edge keep the white display legible.
 d.rounded_rectangle((1,1,CW-2,height-2),radius=47,fill=(29,34,43,190),outline=(232,241,250,42),width=2)
 if state.get('tone')=='urgent':
  d.line((47,2,CW-47,2),fill=(255,151,148,225),width=5)
 c=RED if state.get('tone')=='urgent' else GREEN if state.get('tone')=='success' else SECONDARY
 y=pad;d.text((pad,y),status,font=font(34),fill=c);y+=62
 for line in title:d.text((pad,y),line,font=font(58,True),fill=WHITE);y+=69
 y+=14
 if mode in ('review','streaming'):
  d.text((pad,y),'145/95 mmHg  ·  Pulse 88 bpm',font=font(40),fill=WHITE);y+=62
  d.line((pad,y,CW-pad,y),fill=(230,239,246,45),width=1);y+=42
 if mode=='reading':
  d.text((pad,y),'145 / 95',font=font(82,True),fill=WHITE)
  d.text((pad+395,y+40),'mmHg',font=font(36),fill=SECONDARY)
  d.text((pad,y+96),'Pulse 88 bpm',font=font(43),fill=WHITE);y+=146
 for line in rows:
  if mode!='streaming':d.text((pad,y),line,font=font(43),fill=WHITE)
  y+=57
 if mode in ('listening','dictating','scan'):
  for i,v in enumerate([7,17,29,14,23,35,17,9]):
   px=pad+i*13;d.rounded_rectangle((px,y+18-v//2,px+4,y+18+v//2),radius=2,fill=BLUE)
  y+=45
 if hints:
  y+=25
  for line in hints:d.text((pad,y),line,font=font(32),fill=SECONDARY);y+=43
 return canvas

CARDS={s['id']:card(s) for s in STATES}
def smoothstep(v):
 v=max(0,min(1,v));return v*v*(3-2*v)

# All words use their final line positions from the start. Cached prefix cards
# keep the panel, header, summary, footer, and existing words pixel-identical.
STREAM_WORDS=[];STREAM_CARDS=[]
if TRANSCRIPTION:
 state=next(s for s in STATES if s['id']==TRANSCRIPTION['state'])
 STREAM_CARDS=[CARDS[state['id']]];y=287;f=font(43);index=0
 for line in state['lines']:
  prefix=''
  for word in line.split():
   x=38+round(f.getlength(prefix));sprite=Image.new('RGBA',(math.ceil(f.getlength(word))+3,70),(0,0,0,0))
   ImageDraw.Draw(sprite).text((0,0),word,font=f,fill=WHITE)
   at=TRANSCRIPTION['word_times'][index];STREAM_WORDS.append((at,x,y,sprite))
   c=STREAM_CARDS[-1].copy();c.alpha_composite(sprite,(x,y));STREAM_CARDS.append(c)
   prefix+=word+' ';index+=1
  y+=57
 assert index==len(TRANSCRIPTION['word_times'])

def streaming_card(t):
 fade=TRANSCRIPTION['fade_seconds'];count=sum(t>=at+fade for at,_,_,_ in STREAM_WORDS)
 c=STREAM_CARDS[count]
 if count<len(STREAM_WORDS):
  at,x,y,sprite=STREAM_WORDS[count]
  if t>=at:
   c=c.copy();sprite=sprite.copy();a=smoothstep((t-at)/fade)
   sprite.putalpha(sprite.getchannel('A').point(lambda v:round(v*a)));c.alpha_composite(sprite,(x,y))
 return c

def tilt_indicator(layer,t,state):
 """Scripted visualisation of tilt displacement, not a timed auto-accept UI."""
 gesture=next((g for g in TILTS if g['state']==state['id'] and g['start']<=t<g['end']),None)
 if not gesture:return
 c=CARDS[state['id']];top=BOTTOM-c.height+53;bottom=BOTTOM-53
 x=RIGHT-22 if gesture['direction']=='right' else X+16
 if gesture.get('progress'):
  points=gesture['progress'];progress=float(np.interp(t,[p[0] for p in points],[p[1] for p in points]))
 else:progress=smoothstep((t-gesture['start'])/(gesture['end']-gesture['start']-.10))
 appear=smoothstep((t-gesture['start'])/.13)
 color=(140,235,190) if gesture['direction']=='right' else (255,170,158) if gesture['action']=='decline' else (220,230,240)
 d=ImageDraw.Draw(layer)
 d.rounded_rectangle((x,top,x+6,bottom),radius=3,fill=(228,238,247,int(50*appear)))
 filltop=bottom-(bottom-top)*progress
 if progress>0:
  # Narrow halo and a crisp centre; it remains subordinate to the patient data.
  d.rounded_rectangle((x-3,max(top,filltop-1),x+9,bottom),radius=6,fill=(*color,int(24*appear)))
  d.rounded_rectangle((x,max(top,filltop),x+6,bottom),radius=3,fill=(*color,int(255*appear)))

def closing_title(t):
 layer=Image.new('RGBA',(W,H),(0,0,0,0))
 if t<OUTRO['title_start']:return layer
 alpha=smoothstep((t-OUTRO['title_start'])/(OUTRO['title_full']-OUTRO['title_start']))
 alpha*=1-smoothstep((t-OUTRO['title_fade'])/(OUTRO['end']-OUTRO['title_fade']))
 if OUTRO.get('style')=='brand':
  d=ImageDraw.Draw(layer)
  d.text((W//2,H//2-132),'Lumi',font=font(120,True),fill=(250,252,255,round(255*alpha)),anchor='mt')
  d.text((W//2,H//2+34),'Focusing on the patient',font=font(81),fill=(238,244,249,round(255*alpha)),anchor='mt')
  return layer
 d=ImageDraw.Draw(layer);f=font(99,True)
 baselines=[H//2-128,H//2+10]
 for line,y in zip(OUTRO['lines'],baselines):
  d.text((W//2,y),line,font=f,fill=(250,252,255,round(255*alpha)),anchor='mt')
 # A quiet signature, with spacing rather than a second marketing line.
 letters='LUMI';small=font(31,True);spacing=13
 width=sum(small.getlength(v) for v in letters)+spacing*(len(letters)-1);x=(W-width)/2
 for letter in letters:
  d.text((x,H//2+199),letter,font=small,fill=(185,226,205,round(220*alpha)),anchor='lt')
  x+=small.getlength(letter)+spacing
 return layer
# Reproducible exact timing map, useful for later voice-over/editing.
def active(t):return next((s for s in STATES if s['start']<=t<s['end']),None)
def opacity(t,s):
 if s['id']=='review':return 1.0
 # A short fade; consecutive dictation states replace cleanly without flicker.
 fi=.12 if s['id'].startswith('dictation') else .16
 continuous=any(abs(other['start']-s['end'])<.0001 for other in STATES)
 fo=0 if continuous else .16
 return min(1,max(0,(t-s['start'])/fi),1 if fo==0 else max(0,(s['end']-t)/fo))
def overlay(t,full=True):
 layer=Image.new('RGBA',(W,H),(0,0,0,0));s=active(t)
 if s:
  c=streaming_card(t) if s.get('mode')=='streaming' else CARDS[s['id']];a=opacity(t,s)
  if a<1:c=c.copy();c.putalpha(c.getchannel('A').point(lambda x:int(x*a)))
  layer.alpha_composite(c,(X,BOTTOM-c.height))
  tilt_indicator(layer,t,s)
 # Disclosure only at the opening, separate from the product UI.
 if .1<=t<2.7:
  d=ImageDraw.Draw(layer);d.rounded_rectangle((49,H-120,690,H-55),radius=18,fill=(13,20,30,145))
  d.text((70,H-105),'Prototype demo · composited display',font=font(31),fill=(239,245,250,245))
 if t>=OUTRO['fade_start']:
  darkness=round(255*smoothstep((t-OUTRO['fade_start'])/(OUTRO['fade_end']-OUTRO['fade_start'])))
  layer=Image.alpha_composite(layer,Image.new('RGBA',(W,H),(0,0,0,darkness)))
  layer=Image.alpha_composite(layer,closing_title(t))
 return layer,s

def composite_frame(frame,t):
 base=Image.fromarray(cv2.cvtColor(frame,cv2.COLOR_BGR2RGB)).convert('RGBA');layer,s=overlay(t)
 if s:
  # A subtle, local frost, never a blur across the patient's hands or the whole scene.
  c=CARDS[s['id']];y=BOTTOM-c.height
  crop=base.crop((X,y,RIGHT,BOTTOM));blur=crop.filter(ImageFilter.GaussianBlur(8))
  mask=Image.new('L',crop.size);ImageDraw.Draw(mask).rounded_rectangle((1,1,CW-2,c.height-2),radius=47,fill=int(85*opacity(t,s)))
  base.paste(blur,(X,y),mask)
 return Image.alpha_composite(base,layer).convert('RGB')

def main():
 p=argparse.ArgumentParser();p.add_argument('--input',default=str(ROOT/'source-silent.mp4'));p.add_argument('--output');p.add_argument('--preview',action='store_true');p.add_argument('--alpha',action='store_true');args=p.parse_args()
 (ROOT/'assets').mkdir(exist_ok=True);(ROOT/'frames').mkdir(exist_ok=True)
 for sid,c in CARDS.items():c.save(ROOT/'assets'/f'{sid}.png')
 manifest.write_text(json.dumps({'source':args.input,'width':W,'height':H,'fps':FPS,'hud':{'left':X,'right':RIGHT,'bottom':BOTTOM},'note':'Scripted synthetic composite. Tilt progress is editorially aligned using scene rotation, not device telemetry. Combined save and emergency scenes are not proof of implemented integration.','states':STATES,'tilts':TILTS,'outro':OUTRO,'transcription':TRANSCRIPTION},indent=2))
 if args.preview:
  cap=cv2.VideoCapture(args.input)
  for t in [6.35,6.85,22.65,66.65,73.65,77.4,78.3,79.6,81.3,85.7]:
   if t<SOURCE_DURATION:
    cap.set(cv2.CAP_PROP_POS_MSEC,t*1000);ok,f=cap.read()
   else:ok=True;f=np.zeros((H,W,3),np.uint8)
   if ok:composite_frame(f,t).save(ROOT/'frames'/f'overlay-{t:05.1f}.jpg',quality=94)
  cap.release();print('Preview frames ready',flush=True);return
 if not args.output:return
 duration=OUTRO['end'];nframes=math.ceil(duration*FPS)
 if args.alpha:
  enc=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','warning','-y','-f','rawvideo','-pix_fmt','rgba','-s',f'{W}x{H}','-r',str(FPS),'-i','pipe:0','-an','-c:v','qtrle','-pix_fmt','argb',args.output],stdin=subprocess.PIPE)
  for i in range(nframes):
   enc.stdin.write(overlay(i/FPS)[0].tobytes())
   if i%300==0:print(f'Alpha {i/FPS:.0f}s / {duration:.0f}s',flush=True)
 else:
  dec=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-i',args.input,'-vf',f'fps={FPS}','-an','-f','rawvideo','-pix_fmt','bgr24','pipe:1'],stdout=subprocess.PIPE)
  enc=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','warning','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r',str(FPS),'-i','pipe:0','-an','-c:v','libx264','-preset','fast','-crf','18','-pix_fmt','yuv420p','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-movflags','+faststart',args.output],stdin=subprocess.PIPE)
  i=0;size=W*H*3
  while True:
   raw=dec.stdout.read(size)
   if len(raw)<size:break
   frame=np.frombuffer(raw,np.uint8).reshape(H,W,3)
   enc.stdin.write(composite_frame(frame,i/FPS).tobytes());i+=1
   if i%300==0:print(f'Composite {i/FPS:.0f}s / {duration:.0f}s',flush=True)
  if dec.wait():raise SystemExit('Video decode failed')
  black=np.zeros((H,W,3),np.uint8)
  while i<nframes:
   enc.stdin.write(composite_frame(black,i/FPS).tobytes());i+=1
 enc.stdin.close()
 if enc.wait():raise SystemExit('Video encode failed')
 print('Finished: '+args.output,flush=True)
if __name__=='__main__':main()
