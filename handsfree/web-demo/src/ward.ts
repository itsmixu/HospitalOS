import * as T from 'three';
import {RoundedBoxGeometry} from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import {RoomEnvironment} from 'three/examples/jsm/environments/RoomEnvironment.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import type {Phase} from './guided-model';
export type Shot='wide'|'wrist'|'bedside'|'monitor'|'medicine'|'exit'|'corridor';
type V=[number,number,number];
export async function createWard(canvas:HTMLCanvasElement, onProgress:(n:number)=>void){
 const renderer=new T.WebGLRenderer({canvas,antialias:true});renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.shadowMap.enabled=true;renderer.shadowMap.type=T.PCFSoftShadowMap;renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=.98;renderer.outputColorSpace=T.SRGBColorSpace;
 const scene=new T.Scene();scene.background=new T.Color('#bfcbd0');scene.fog=new T.Fog('#c8d2d6',24,46);
 const pmrem=new T.PMREMGenerator(renderer);const roomEnv=new RoomEnvironment();scene.environment=pmrem.fromScene(roomEnv,.04).texture;roomEnv.dispose();pmrem.dispose();scene.environmentIntensity=.25;
 const camera=new T.PerspectiveCamera(52,1,.025,70);const mats=new Map<string,T.MeshStandardMaterial>();
 function mat(color:string,roughness=.65,metalness=0){const key=color+roughness+metalness;let m=mats.get(key);if(!m){m=new T.MeshStandardMaterial({color,roughness,metalness});mats.set(key,m)}return m}
 function box(w:number,h:number,d:number,pos:V,color:string,r=.025,metal=0){const m=new T.Mesh(r?new RoundedBoxGeometry(w,h,d,3,Math.min(r,w/3,h/3,d/3)):new T.BoxGeometry(w,h,d),mat(color,metal?.28:.7,metal));m.position.set(...pos);m.castShadow=true;m.receiveShadow=true;scene.add(m);return m}
 function sphere(scale:V,pos:V,color:string){const m=new T.Mesh(new T.SphereGeometry(1,32,24),mat(color));m.scale.set(...scale);m.position.set(...pos);m.castShadow=true;m.receiveShadow=true;scene.add(m);return m}
 function cylinder(a:V,b:V,r:number,color:string){const av=new T.Vector3(...a),bv=new T.Vector3(...b);const m=new T.Mesh(new T.CylinderGeometry(r,r,av.distanceTo(bv),24),mat(color,.45,color==='#aab9c2'?.65:0));m.position.copy(av).add(bv).multiplyScalar(.5);m.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),bv.sub(av).normalize());m.castShadow=true;scene.add(m);return m}
 function cable(points:V[],color:string,r=.008){const curve=new T.CatmullRomCurve3(points.map(p=>new T.Vector3(...p)));const m=new T.Mesh(new T.TubeGeometry(curve,36,r,8,false),mat(color));m.castShadow=true;scene.add(m);return m}
 function panel(w:number,h:number,pos:V,draw:(ctx:CanvasRenderingContext2D)=>void,rotation:V=[0,0,0]){const c=document.createElement('canvas');c.width=1024;c.height=Math.round(1024*h/w);const ctx=c.getContext('2d')!;draw(ctx);const texture=new T.CanvasTexture(c);texture.colorSpace=T.SRGBColorSpace;texture.anisotropy=renderer.capabilities.getMaxAnisotropy();const m=new T.Mesh(new T.PlaneGeometry(w,h),new T.MeshBasicMaterial({map:texture}));m.position.set(...pos);m.rotation.set(...rotation);scene.add(m);return {mesh:m,ctx,texture}}
 function textPanel(text:string,w:number,h:number,pos:V,bg='#244e5d',fg='#fff',rotation:V=[0,0,0]){return panel(w,h,pos,c=>{c.fillStyle=bg;c.fillRect(0,0,c.canvas.width,c.canvas.height);c.fillStyle=fg;c.font='500 170px Arial';c.textAlign='center';c.textBaseline='middle';c.fillText(text,512,c.canvas.height/2)},rotation)}
 scene.add(new T.HemisphereLight('#e1efff','#6f7775',.72));const sun=new T.DirectionalLight('#fff4df',2.4);sun.position.set(-3.9,3.1,.6);sun.castShadow=true;sun.shadow.mapSize.set(2048,2048);Object.assign(sun.shadow.camera,{left:-8,right:9,top:8,bottom:-9,near:.1,far:25});sun.shadow.normalBias=.035;sun.shadow.bias=-.0002;sun.shadow.radius=5;scene.add(sun);const fill=new T.PointLight('#cce9ff',8,13,2);fill.position.set(1.5,2.7,0);scene.add(fill);
 // Full-size room and adjoining corridor. Repeated architectural details create depth.
 box(13,.16,34,[1,-.1,-9],'#a7b3b5');for(let z=-24;z<5;z+=1.5){box(13,.003,.009,[1,0,z],'#88999e',0);for(let x=-5;x<8;x+=1.5)box(.009,.003,1.5,[x,0,z+.75],'#98a6a9',0)}
 box(8,3.3,.18,[-1,1.65,-3.6],'#94aaa9');box(.18,3.3,8.6,[-5,1.65,.6],'#e4e8e4');box(8,.15,8.5,[-1,3.35,.5],'#e9ece8');box(.15,3.3,30,[7.8,1.65,-10],'#d8e1e1');box(.15,3.3,20,[3.1,1.65,-14],'#d2dedd');
 box(8,.14,.08,[-1,.18,-3.45],'#9dacae');box(.08,.14,8,[-4.86,.18,.4],'#9dacae');box(.07,.2,29,[7.67,.85,-10],'#87a4ac');box(.08,.08,28,[7.64,1.04,-10],'#d3dfdf');
 for(let z=-21;z<4;z+=4.5){box(2.7,.04,.65,[5.4,3.13,z],'#f2f6f4');const light=new T.PointLight('#e0f1ff',4,8,2);light.position.set(5.4,2.9,z);scene.add(light);box(.045,2.3,1.35,[7.69,1.15,z],'#657f88');box(.035,2.1,1.17,[7.65,1.13,z],'#adc1c5');box(.04,.5,.42,[7.61,1.6,z],'#dce8e7');cylinder([7.58,.95,z-.35],[7.58,.95,z-.1],.018,'#aab9c2');textPanel(z<-10?'08':'12',.43,.28,[7.59,1.96,z],'#365764','#fff',[0,-Math.PI/2,0]);}
 textPanel('08',.55,.4,[3.2,2.15,-15],'#215061','#fff',[0,Math.PI/2,0]);box(.1,2.5,1.6,[3.15,1.25,-15],'#7a939a');
 box(5,.78,.045,[-1.5,.43,-3.48],'#b7a38a');for(let x=-3.9;x<1;x+=.16)box(.012,.75,.01,[x,.43,-3.452],'#9d8d78',0);
 box(.06,.16,20,[3.2,.85,-14],'#79989e');box(.075,.06,20,[3.21,.97,-14],'#c6d4d3');
 textPanel('WARD 3',1.3,.28,[5.4,2.65,-8],'#264c59','#eaf4f4');
 // Window, curtain, service rail, fittings and furniture.
 box(.04,2.25,3.7,[-4.87,1.9,-.8],'#a8c9da');for(let z=-2.6;z<1.2;z+=1.2)box(.06,2.3,.045,[-4.8,1.9,z],'#f3f4ed');box(.24,.08,3.9,[-4.78,.74,-.8],'#e4e8de');
 for(let i=0;i<22;i++){const m=cylinder([-4.54,3.1,1.1+i*.065],[-4.54,.25,1.1+i*.065],.07,i%2?'#70939e':'#8aabb3');m.castShadow=false;}
 box(3.6,.32,.13,[-1.4,1.63,-3.43],'#afc0c3');for(const x of [-2.7,-2.4,-.2,.1]){box(.17,.2,.05,[x,1.63,-3.33],'#edf1eb');sphere([.027,.027,.015],[x,1.63,-3.295],x< -1?'#709487':'#c5a973')}
 box(.75,.84,.65,[-3.15,.45,-2.65],'#d6c4a8');box(.8,.055,.7,[-3.15,.89,-2.65],'#edece2');for(let y=.2;y<.8;y+=.24){box(.68,.018,.01,[-3.15,y,-2.316],'#aa9a84',0);cylinder([-3.31,y+.1,-2.29],[-3.02,y+.1,-2.29],.012,'#aab9c2')}
 cylinder([-3.12,.92,-2.6],[-3.12,1.12,-2.6],.06,'#b8d9e1');box(.13,.03,.13,[-3.12,1.14,-2.6],'#edf4f2');
 box(.6,.1,.6,[-3.5,.48,.1],'#728b8d');box(.6,.7,.12,[-3.5,.88,-.17],'#728b8d');for(let x of [-3.73,-3.27])for(let z of [-.12,.33])cylinder([x,.06,z],[x,.48,z],.025,'#aab9c2');
 // True-scale bed and rigged human. All physical dimensions are metres.
 const bx=-1.45;
 box(.8,.12,1.5,[bx,.28,-.82],'#bac9cd');box(.35,.4,.55,[bx,.54,-.82],'#b1c2c7');
 box(1.12,.12,2.3,[bx,.81,-.82],'#d1dddd');box(1.06,.2,2.21,[bx,.98,-.82],'#edece5',.09);
 box(.62,.105,.37,[bx,1.105,-1.68],'#faf7ef',.055);
 for(const x of [bx-.43,bx+.43])for(const z of [-1.63,.02]){cylinder([x,.13,z],[x,.3,z],.034,'#aab9c2');const w=new T.Mesh(new T.CylinderGeometry(.075,.075,.055,24),mat('#3a4a50'));w.rotation.z=Math.PI/2;w.position.set(x,.13,z);scene.add(w)}
 for(const z of [-2.01,.37]){box(1.14,.49,.07,[bx,.97,z],'#c9d9da',.045);box(.96,.085,.075,[bx,1.2,z],'#f0f2ec');}
 for(const x of [bx-.59,bx+.59]){for(const z of [-1.6,-.75])cylinder([x,.82,z],[x,1.16,z],.02,'#aab9c2');box(.045,.08,1.04,[x,1.19,-1.16],'#e6ece7');}
 const loader=new GLTFLoader();
 const human=await loader.loadAsync('assets/patient.glb',e=>onProgress(e.total?e.loaded/e.total:.4));
 onProgress(1);
 const body=human.scene;body.updateMatrixWorld(true);
 // Cull only the lower body hidden under bedding; this avoids skin/cloth
 // intersections while retaining the imported head, shoulders, arms and hands.
 body.traverse(o=>{if(o instanceof T.SkinnedMesh){
   o.skeleton.update();const geometry=o.geometry.clone(),indices=geometry.index!;
   const hidden=new Uint8Array(geometry.attributes.position.count),v=new T.Vector3();
   for(let i=0;i<hidden.length;i++){o.getVertexPosition(i,v).applyMatrix4(o.matrixWorld);hidden[i]=Number(v.y<1.04&&Math.abs(v.x)<.31);}
   const kept:number[]=[];for(let i=0;i<indices.count;i+=3){const a=indices.getX(i),b=indices.getX(i+1),c=indices.getX(i+2);if(!(hidden[a]&&hidden[b]&&hidden[c]))kept.push(a,b,c);}
   geometry.setIndex(kept);o.geometry=geometry;
 }});
 const skin=new T.MeshStandardMaterial({color:'#bc937f',roughness:.81});
 body.traverse(o=>{if(o instanceof T.Mesh){o.material=skin;o.castShadow=true;o.receiveShadow=true;}});
 function aim(name:string,childName:string,target:T.Vector3){
   body.updateMatrixWorld(true);const bone=body.getObjectByName(name)!;const child=body.getObjectByName(childName)!;
   const origin=bone.getWorldPosition(new T.Vector3());const old=child.getWorldPosition(new T.Vector3()).sub(origin).normalize();
   const delta=new T.Quaternion().setFromUnitVectors(old,target.sub(origin).normalize());
   const world=bone.getWorldQuaternion(new T.Quaternion()).premultiply(delta);
   bone.quaternion.copy(bone.parent!.getWorldQuaternion(new T.Quaternion()).invert().multiply(world));
 }
 for(const [side,sign] of [['l',1],['r',-1]] as const){
   aim('upperarm_'+side,'lowerarm_'+side,new T.Vector3(.28*sign,1.11,.02));
   aim('lowerarm_'+side,'hand_'+side,new T.Vector3(.36*sign,.88,.045));
 }
 const patient=new T.Group();patient.rotation.x=-Math.PI/2;patient.position.set(bx,1.19,-.075);patient.add(body);scene.add(patient);scene.updateMatrixWorld(true);
 const head=body.getObjectByName('head')!;
 // A soft cap and pillow make the untextured CC0 head read naturally at bedside.
 const cap=sphere([.085,.075,.075],[bx,1.255,-1.66],'#5d5650');head.attach(cap);
 // Fitted gown, neckline, seams, and a blanket shaped over the legs.
 const gown=sphere([.22,.098,.32],[bx,1.25,-1.16],'#7797a2');
 const neck=sphere([.058,.008,.055],[bx,1.355,-1.4],'#bc937f');
 for(const side of [-1,1])cylinder([bx+side*.17,1.25,-1.41],[bx+side*.26,1.235,-1.24],.071,'#7797a2');
 for(let i=0;i<3;i++)sphere([.008,.004,.008],[bx,1.366,-1.35+i*.07],'#dce9e7');
 // Draped cloth follows the body volume, with shallow woven folds.
 const cloth=new T.PlaneGeometry(.96,1.22,50,65);const cp=cloth.attributes.position;
 for(let i=0;i<cp.count;i++){
  const x=cp.getX(i),z=cp.getY(i);const arch=Math.pow(Math.max(0,Math.cos(x/.96*Math.PI)),.7);
  const fold=Math.sin(z*32+x*7)*.004+Math.sin(x*45+z*12)*.002;
  cp.setXYZ(i,x,1.105+.18*arch+fold,z-.51);
 }
 cloth.computeVertexNormals();const cover=new T.Mesh(cloth,new T.MeshStandardMaterial({color:'#d9e4df',roughness:.98,side:T.DoubleSide}));cover.position.x=bx;cover.castShadow=true;cover.receiveShadow=true;scene.add(cover);
 // Wristband, label and QR are children of the actual wrist bone, not the room.
 const hand=body.getObjectByName('hand_l')!;const wrist=hand.getWorldPosition(new T.Vector3());
 const forearm=body.getObjectByName('lowerarm_l')!.getWorldPosition(new T.Vector3());
 const wristBand=new T.Group();wristBand.position.copy(wrist).addScaledVector(wrist.clone().sub(forearm).normalize(),-.018);scene.add(wristBand);
 const ring=new T.Mesh(new T.CylinderGeometry(.031,.031,.043,40,1,true),mat('#fffdf5'));
 ring.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),wrist.clone().sub(forearm).normalize());wristBand.add(ring);
 const plaque=new T.Mesh(new RoundedBoxGeometry(.047,.005,.055,3,.003),mat('#fffef8'));plaque.position.y=.028;wristBand.add(plaque);
 const qr=await new T.TextureLoader().loadAsync('wristband-qr.png');qr.colorSpace=T.SRGBColorSpace;qr.anisotropy=8;
 const qrMesh=new T.Mesh(new T.PlaneGeometry(.041,.041),new T.MeshBasicMaterial({map:qr}));qrMesh.rotation.x=-Math.PI/2;qrMesh.position.set(0,.031,0);wristBand.add(qrMesh);hand.attach(wristBand);
 const restingHand=hand.quaternion.clone();
 // Blood-pressure monitor, cuff, tubing and wheeled stand.
 const cx=.04,cz=-1.42;box(.48,.08,.46,[cx,.16,cz],'#b8c8cc');cylinder([cx,.19,cz],[cx,.94,cz],.025,'#aab9c2');
 for(const x of [cx-.2,cx+.2])for(const z of [cz-.18,cz+.18])sphere([.045,.045,.045],[x,.095,z],'#36474d');
 box(.55,.065,.43,[cx,.96,cz],'#e4e8e2');const screenBody=box(.49,.34,.055,[cx,1.18,cz-.08],'#e7e9e4',.025);screenBody.rotation.x=-.13;
 box(.4,.25,.014,[cx,1.19,cz-.038],'#17292d',.012);const monitor=panel(.385,.235,[cx,1.19,cz-.026],()=>{});
 sphere([.012,.012,.008],[cx+.204,1.08,cz-.039],'#68ae9f');
 const cuff=box(.14,.08,.16,[bx-.25,1.265,-1.27],'#334c61');
 cable([[bx-.25,1.3,-1.24],[bx-.65,1.12,-1.0],[-.6,.82,-1.02],[cx-.23,1.0,cz+.1]],'#34474b',.009);
 // Medication trolley and a small, clearly visible cup with one tablet.
 box(.52,.065,.42,[.08,.93,.05],'#e0e6df');for(const x of [-.12,.28])cylinder([x,.12,.05],[x,.93,.05],.017,'#aab9c2');
 box(.4,.12,.28,[.08,.7,.05],'#c2d0d0');
 const cup=new T.Group();cup.position.set(.08,.995,.05);scene.add(cup);
 const cupMesh=new T.Mesh(new T.CylinderGeometry(.037,.027,.065,32,1,true),new T.MeshStandardMaterial({color:'#f1eee2',side:T.DoubleSide,roughness:.45}));cup.add(cupMesh);
 const pill=new T.Mesh(new T.CapsuleGeometry(.009,.012,4,12),mat('#fffdf7'));pill.rotation.z=Math.PI/2;pill.position.y=.015;cup.add(pill);
 const glove=new T.Group();const palm=new T.Mesh(new T.SphereGeometry(1,24,16),mat('#92bccc'));palm.scale.set(.041,.023,.064);glove.add(palm);
 for(let i=0;i<4;i++){const f=new T.Mesh(new T.CapsuleGeometry(.009,.049,4,10),mat('#92bccc'));f.rotation.x=Math.PI/2;f.position.set(-.028+i*.018,0,-.065);glove.add(f)}
 const thumb=new T.Mesh(new T.CapsuleGeometry(.013,.035,4,10),mat('#92bccc'));thumb.rotation.z=-.7;thumb.rotation.x=Math.PI/2;thumb.position.set(.043,0,-.02);glove.add(thumb);
 const sleeve=new T.Mesh(new T.CylinderGeometry(.046,.057,.28,20),mat('#638d9b'));sleeve.rotation.x=Math.PI/2;sleeve.position.z=.19;sleeve.visible=false;glove.add(sleeve);scene.add(glove);
 const nurseArm=new T.Mesh(new T.CylinderGeometry(.036,.065,1,24),mat('#638d9b'));nurseArm.castShadow=true;scene.add(nurseArm);
 const wristTarget=new T.Vector3();qrMesh.getWorldPosition(wristTarget);
 const poses:Record<Shot,{pos:V;look:V;fov:number}>={
  wide:{pos:[1.6,1.65,2.5],look:[-1.25,1.12,-1.02],fov:50},
  wrist:{pos:[-.65,1.64,-.33],look:[wristTarget.x,wristTarget.y,wristTarget.z],fov:45},
  bedside:{pos:[.6,1.67,.55],look:[-1.2,1.22,-1.2],fov:51},
  monitor:{pos:[.38,1.54,-.15],look:[-.04,1.16,-1.42],fov:48},
  medicine:{pos:[-.55,1.62,-.9],look:[-1.3,1.32,-1.53],fov:57},
  exit:{pos:[3.8,1.65,.8],look:[5.3,1.5,-7],fov:53},
  corridor:{pos:[5.3,1.65,-9],look:[5.3,1.5,-20],fov:54}
 };
 let shot:Shot='wide',from=poses.wide,to=poses.wide,transitionAt=0;
 let position=new T.Vector3(...from.pos),look=new T.Vector3(...from.look),fov=from.fov;
 let lastW=0,lastH=0,lastMonitor=-1;
 const smooth=(v:number)=>v*v*(3-2*v);
 function drawMonitor(time:number,finished:boolean,measuring:boolean){
  const c=monitor.ctx,w=c.canvas.width,h=c.canvas.height;c.fillStyle='#091c23';c.fillRect(0,0,w,h);
  c.fillStyle='#a2c3cc';c.font='500 33px Arial';c.fillText('NIBP',45,53);c.textAlign='right';c.fillText('mmHg',w-42,53);c.textAlign='left';
  c.fillStyle=finished?'#eaf7f3':'#78969d';c.font='600 165px Arial';c.fillText(finished?'145/95':measuring?'… / …':'— / —',39,236);
  c.fillStyle='#90d5be';c.font='500 45px Arial';c.fillText(finished?'PULSE    88 bpm':measuring?'Measuring':'Ready',45,332);
  c.fillStyle='#6c8b94';c.font='25px Arial';c.fillText('SIMULATED MONITOR',45,h-34);
  if(measuring){c.fillStyle='#6bcbb5';c.fillRect(45,h-82,(w-90)*((time%4)/4),4)}
  monitor.texture.needsUpdate=true;
 }
 return {render(time:number,next:Shot,phase:Phase,elapsed:number,reduced:boolean){
  const w=canvas.clientWidth,h=canvas.clientHeight;if(w!==lastW||h!==lastH){renderer.setSize(w,h,false);camera.aspect=w/h;lastW=w;lastH=h;}
  if(next!==shot){from={pos:position.toArray() as V,look:look.toArray() as V,fov};to=poses[next];shot=next;transitionAt=time;}
  const travel=shot==='corridor'?11:shot==='exit'?3.5:2.5;
  const p=reduced?1:smooth(Math.min(1,(time-transitionAt)/travel));position.lerpVectors(new T.Vector3(...from.pos),new T.Vector3(...to.pos),p);look.lerpVectors(new T.Vector3(...from.look),new T.Vector3(...to.look),p);fov=T.MathUtils.lerp(from.fov,to.fov,p);
  camera.position.copy(position);camera.lookAt(look);camera.fov=fov+(camera.aspect<1?10:0);camera.updateProjectionMatrix();
  const breathe=reduced?0:Math.sin(time*1.3)*.0015;gown.position.y=1.25+breathe;
  hand.quaternion.copy(restingHand);if(phase==='medication'&&!reduced)hand.rotateX(Math.sin(Math.min(1,elapsed/8000)*Math.PI)*.12);
  const handling=phase==='medication';glove.visible=handling;nurseArm.visible=handling;
  if(phase==='procedure'){glove.position.set(cx+.15,1.03,cz+.2);glove.rotation.set(-.3,0,-.3);}
  if(phase==='medication'){
    const t=Math.min(1,Math.max(0,elapsed-1500)/5500),reach=smooth(Math.min(1,t*2));
    const cupFrom=new T.Vector3(.08,.995,.05),cupTo=new T.Vector3(bx+.22,1.46,-1.44);
    cup.position.lerpVectors(cupFrom,cupTo,reach);cup.position.y+=Math.sin(t*Math.PI)*.1;
    glove.position.copy(cup.position).add(new T.Vector3(.05,-.04,.07));glove.rotation.set(.1,-.5,-.25);pill.visible=t<.7;
    if(t>.7)cup.rotation.z=(t-.7)*1.8;
  }else{cup.position.set(.08,.995,.05);cup.rotation.z=0;pill.visible=!['note-ready','transcribing','note-paused','review','saved','emergency','handover','outro','complete'].includes(phase);}
  if(handling){
    glove.updateMatrixWorld(true);
    const wristEnd=glove.localToWorld(new T.Vector3(0,0,.045));
    const elbow=new T.Vector3(.22,-.38,-.12).applyQuaternion(camera.quaternion).add(camera.position);
    const direction=wristEnd.clone().sub(elbow);nurseArm.position.copy(elbow).add(wristEnd).multiplyScalar(.5);
    nurseArm.scale.y=direction.length();nurseArm.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),direction.normalize());
  }
  const finished=['capture-ready','capturing','reading','medication','note-ready','transcribing','note-paused','review','saved','emergency','handover'].includes(phase);
  if(time-lastMonitor>.15||time<lastMonitor){drawMonitor(time,finished,['procedure','request','receipt','measuring'].includes(phase));lastMonitor=time;}
  renderer.render(scene,camera);const marker=qrMesh.getWorldPosition(new T.Vector3()).project(camera);
  return {x:(marker.x*.5+.5)*100,y:(-.5*marker.y+.5)*100,visible:marker.z<1};
 },reset(){shot='wide';from=to=poses.wide;position.set(...from.pos);look.set(...from.look);fov=from.fov;transitionAt=0;}};
}
