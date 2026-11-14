import * as T from 'three';
import {RoundedBoxGeometry} from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import {RoomEnvironment} from 'three/examples/jsm/environments/RoomEnvironment.js';
import type {Shot} from './story';
type V=[number,number,number];
export function createCinematic(canvas:HTMLCanvasElement){
 const renderer=new T.WebGLRenderer({canvas,antialias:true});renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.shadowMap.enabled=true;renderer.shadowMap.type=T.PCFSoftShadowMap;renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=.98;renderer.outputColorSpace=T.SRGBColorSpace;
 const scene=new T.Scene();scene.background=new T.Color('#bfcbd0');scene.fog=new T.Fog('#c8d2d6',24,46);
 const pmrem=new T.PMREMGenerator(renderer);const roomEnv=new RoomEnvironment();scene.environment=pmrem.fromScene(roomEnv,.04).texture;roomEnv.dispose();pmrem.dispose();scene.environmentIntensity=.35;
 const camera=new T.PerspectiveCamera(52,1,.025,70);const mats=new Map<string,T.MeshStandardMaterial>();
 function mat(color:string,roughness=.65,metalness=0){const key=color+roughness+metalness;let m=mats.get(key);if(!m){m=new T.MeshStandardMaterial({color,roughness,metalness});mats.set(key,m)}return m}
 function box(w:number,h:number,d:number,pos:V,color:string,r=.025,metal=0){const m=new T.Mesh(r?new RoundedBoxGeometry(w,h,d,3,Math.min(r,w/3,h/3,d/3)):new T.BoxGeometry(w,h,d),mat(color,metal?.28:.7,metal));m.position.set(...pos);m.castShadow=true;m.receiveShadow=true;scene.add(m);return m}
 function sphere(scale:V,pos:V,color:string){const m=new T.Mesh(new T.SphereGeometry(1,32,24),mat(color));m.scale.set(...scale);m.position.set(...pos);m.castShadow=true;m.receiveShadow=true;scene.add(m);return m}
 function cylinder(a:V,b:V,r:number,color:string){const av=new T.Vector3(...a),bv=new T.Vector3(...b);const m=new T.Mesh(new T.CylinderGeometry(r,r,av.distanceTo(bv),24),mat(color,.45,color==='#aab9c2'?.65:0));m.position.copy(av).add(bv).multiplyScalar(.5);m.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),bv.sub(av).normalize());m.castShadow=true;scene.add(m);return m}
 function cable(points:V[],color:string,r=.008){const curve=new T.CatmullRomCurve3(points.map(p=>new T.Vector3(...p)));const m=new T.Mesh(new T.TubeGeometry(curve,36,r,8,false),mat(color));m.castShadow=true;scene.add(m);return m}
 function panel(w:number,h:number,pos:V,draw:(ctx:CanvasRenderingContext2D)=>void,rotation:V=[0,0,0]){const c=document.createElement('canvas');c.width=1024;c.height=Math.round(1024*h/w);const ctx=c.getContext('2d')!;draw(ctx);const texture=new T.CanvasTexture(c);texture.colorSpace=T.SRGBColorSpace;texture.anisotropy=renderer.capabilities.getMaxAnisotropy();const m=new T.Mesh(new T.PlaneGeometry(w,h),new T.MeshBasicMaterial({map:texture}));m.position.set(...pos);m.rotation.set(...rotation);scene.add(m);return {mesh:m,ctx,texture}}
 function textPanel(text:string,w:number,h:number,pos:V,bg='#244e5d',fg='#fff',rotation:V=[0,0,0]){return panel(w,h,pos,c=>{c.fillStyle=bg;c.fillRect(0,0,c.canvas.width,c.canvas.height);c.fillStyle=fg;c.font='500 170px Arial';c.textAlign='center';c.textBaseline='middle';c.fillText(text,512,c.canvas.height/2)},rotation)}
 scene.add(new T.HemisphereLight('#e1efff','#6f7775',1.2));const sun=new T.DirectionalLight('#fff4df',2.4);sun.position.set(-5,7,2);sun.castShadow=true;sun.shadow.mapSize.set(2048,2048);Object.assign(sun.shadow.camera,{left:-8,right:9,top:8,bottom:-9,near:.1,far:25});sun.shadow.normalBias=.035;sun.shadow.bias=-.0002;sun.shadow.radius=5;scene.add(sun);const fill=new T.PointLight('#cce9ff',8,13,2);fill.position.set(1.5,2.7,0);scene.add(fill);
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
 // Bed: caster chassis, lift, mattress, pillow and rounded articulated rails.
 const bx=-1.45;box(1.15,.16,2.2,[bx,.28,-.8],'#c2cdcd');box(.5,.48,.9,[bx,.53,-.8],'#aebdc0');box(1.85,.2,3.45,[bx,.8,-.8],'#d5dedd');box(1.75,.25,3.3,[bx,1.01,-.8],'#f0eee6',.1);box(1.25,.17,.67,[bx,1.21,-2.02],'#f5f3e9',.08);
 for(const x of [bx-.65,bx+.65])for(const z of [-2.1,.5]){cylinder([x,.12,z],[x,.29,z],.07,'#aab9c2');const w=new T.Mesh(new T.CylinderGeometry(.1,.1,.075,24),mat('#414e53'));w.rotation.z=Math.PI/2;w.position.set(x,.11,z);scene.add(w)}
 for(const z of [-2.57,1]){box(1.9,.65,.11,[bx,1.02,z],'#bdcfd1',.05);box(1.6,.12,.13,[bx,1.32,z],'#e4eae5',.04)}
 for(const x of [bx-.94,bx+.94]){for(const z of [-1.65,-.35])cylinder([x,.87,z],[x,1.35,z],.035,'#aab9c2');box(.065,.1,1.65,[x,1.38,-1],'#e5eae5',.025);box(.065,.12,1.65,[x,1.05,-1],'#cbd7d5',.025)}
 // Patient is a coherent articulated 3D mannequin; wrists and hands share a continuous arm.
 const skin='#bd9683';sphere([.235,.235,.3],[bx,1.39,-1.97],skin);sphere([.218,.18,.22],[bx,1.39,-2.11],'#92938e');sphere([.036,.045,.06],[bx,1.625,-1.88],skin);for(const x of [bx-.09,bx+.09]){cable([[x-.032,1.601,-1.98],[x,1.61,-1.974],[x+.032,1.601,-1.98]],'#6e6963',.005)};cable([[bx-.045,1.566,-1.76],[bx,1.574,-1.752],[bx+.045,1.566,-1.76]],'#966e61',.006);for(const x of [bx-.23,bx+.23])sphere([.045,.075,.04],[x,1.4,-1.98],skin);
 cylinder([bx,1.22,-1.74],[bx,1.24,-1.57],.105,skin);const torso=sphere([.42,.2,.63],[bx,1.25,-1.03],'#8099a7');
 // Lower body under blanket. Curved top surface retains the shape of legs rather than a box.
 sphere([.58,.19,1.05],[bx,1.19,.02],'#d8e3df');box(1.48,.1,1.85,[bx,1.17,.05],'#d8e3df',.045);
 for(let i=0;i<7;i++)cable([[bx-.65,1.23,-.65+i*.23],[bx-.3,1.36,-.64+i*.23],[bx+.25,1.35,-.66+i*.23],[bx+.65,1.24,-.67+i*.23]],'#c7d6d2',.006);
 // Right arm rests ON the mattress, with the band actually wrapped around its wrist.
 const shoulder:V=[bx+.32,1.29,-1.43],elbow:V=[bx+.62,1.2,-1.0],wrist:V=[bx+.69,1.21,-.37];
 cylinder(shoulder,elbow,.105,'#8099a7');sphere([.105,.1,.11],elbow,skin);cylinder(elbow,wrist,.072,skin);sphere([.09,.052,.145],[bx+.69,1.21,-.2],skin);
 for(let i=0;i<4;i++)cylinder([bx+.64+i*.031,1.215,-.12],[bx+.64+i*.031,1.207,.015-(i%3)*.012],.018,skin);cylinder([bx+.61,1.205,-.22],[bx+.55,1.2,-.1],.024,skin);
 cylinder([bx-.3,1.29,-1.44],[bx-.62,1.21,-.95],.1,'#8099a7');cylinder([bx-.62,1.21,-.95],[bx-.6,1.2,-.35],.07,skin);sphere([.09,.05,.15],[bx-.6,1.2,-.2],skin);
 // Cylinder axis follows forearm (Z). White band encloses it, with label tangent on top.
 const band=new T.Mesh(new T.CylinderGeometry(.079,.079,.14,48,1,true),mat('#fcfcf5',.45));band.rotation.x=Math.PI/2;band.position.set(bx+.687,1.21,-.4);band.castShadow=true;scene.add(band);
 box(.117,.012,.135,[bx+.687,1.291,-.4],'#fffef7',.008);
 const qr=new T.TextureLoader().load('wristband-qr.png');qr.colorSpace=T.SRGBColorSpace;qr.anisotropy=8;const qrMesh=new T.Mesh(new T.PlaneGeometry(.095,.095),new T.MeshBasicMaterial({map:qr}));qrMesh.rotation.x=-Math.PI/2;qrMesh.position.set(bx+.687,1.298,-.413);scene.add(qrMesh);
 const wristTarget=new T.Vector3(bx+.687,1.3,-.4);
 // ECG cart with caster feet, leads, paper printer and illuminated animated display.
 const cx=.52,cz=-1.85;box(.78,.12,.62,[cx,.23,cz],'#bcc9cd');cylinder([cx,.25,cz],[cx,1.05,cz],.045,'#aab9c2');for(const x of [cx-.3,cx+.3])for(const z of [cz-.22,cz+.22])sphere([.06,.06,.06],[x,.15,z],'#344950');box(.85,.14,.67,[cx,1.06,cz],'#e1e6e2');box(.76,.54,.09,[cx,1.42,cz-.21],'#d9e0dd',.045);box(.65,.41,.02,[cx,1.43,cz-.151],'#172a30',.02);
 const monitor=panel(.61,.36,[cx,1.43,cz-.138],()=>{});for(let i=0;i<5;i++)box(.055,.015,.038,[cx-.28+i*.095,1.145,cz+.1],i===4?'#6ca5a8':'#a7babf',.008);box(.27,.006,.36,[cx+.1,1.143,cz+.17],'#f6f5eb',.001);for(let i=0;i<9;i++)box(.22,.001,.002,[cx+.1,1.148,cz+.02+i*.035],'#bbc0b8',0);
 for(let i=0;i<3;i++){const px=bx+.1+(i%2)*.2,pz=-1.35+i*.24;sphere([.035,.008,.035],[px,1.44-i*.025,pz],'#dce5df');cable([[px,1.447-i*.025,pz],[bx+.5,1.3,pz+.1],[.1,.8,-1.1],[cx-.33,1.13,cz+.12+i*.06]],['#ab6f62','#91a775','#ded6a1'][i],.009)}
 // Nurse's gloved hand only appears while handling the ECG cart.
 const glove=new T.Group();const palm=new T.Mesh(new T.SphereGeometry(1,24,16),mat('#98c7da'));palm.scale.set(.065,.035,.09);glove.add(palm);for(let i=0;i<4;i++){const f=new T.Mesh(new T.CapsuleGeometry(.014,.07,4,12),mat('#98c7da'));f.rotation.x=Math.PI/2;f.position.set(-.04+i*.025,0,-.09);glove.add(f)}const thumb=new T.Mesh(new T.CapsuleGeometry(.02,.055,4,12),mat('#98c7da'));thumb.rotation.z=-.7;thumb.rotation.x=Math.PI/2;thumb.position.set(.075,0,-.02);glove.add(thumb);scene.add(glove);
 const positions:Record<Shot,{from:V,to:V,look:V,fov?:number}>={
  wide:{from:[2.5,1.78,3.7],to:[2.1,1.72,3.3],look:[-1.1,1.15,-1.1]},
  wrist:{from:[-.14,2.05,.16],to:[-.42,1.87,-.04],look:[-.8,1.29,-.4],fov:43},
  bedside:{from:[1.3,1.75,2.05],to:[1.25,1.72,1.9],look:[-.7,1.2,-1.15]},
  ecg:{from:[1.48,1.75,.45],to:[1.42,1.7,.3],look:[-.3,1.3,-1.7]},
  exit:{from:[2.7,1.72,1.2],to:[4.9,1.72,.25],look:[5.25,1.5,-12]},
  corridor:{from:[5.3,1.73,-.5],to:[5.3,1.73,-12.7],look:[5.3,1.5,-21]}
 };
 let lastTime=-1;let lastW=0,lastH=0;
 function drawTrace(t:number,complete:boolean){const ctx=monitor.ctx,w=ctx.canvas.width,h=ctx.canvas.height;ctx.fillStyle='#0b1d24';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#19343b';ctx.lineWidth=1;for(let x=0;x<w;x+=32){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke()}for(let y=0;y<h;y+=32){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke()}ctx.font='500 30px Arial';ctx.fillStyle='#d4e8e8';ctx.fillText(complete?'12-LEAD ECG  /  COMPLETE':'12-LEAD ECG  /  RECORDING',28,44);ctx.fillStyle='#7bbbbe';ctx.font='22px Arial';ctx.fillText('SYNTHETIC DEMO TRACE',28,h-25);for(let row=0;row<3;row++){ctx.beginPath();ctx.strokeStyle=['#74d4ba','#9cdbb8','#72b9dc'][row];ctx.lineWidth=3;for(let x=25;x<w-30;x++){const p=((x+t*100+row*30)%220)/220;let y=Math.sin(p*6.28)*4;if(p>.34&&p<.37)y-=20;if(p>=.37&&p<.4)y+=85;if(p>=.4&&p<.44)y-=30;ctx.lineTo(x,125+row*125-y)}ctx.stroke()}monitor.texture.needsUpdate=true;}
 return {render(time:number,shot:Shot,p:number,ecg:boolean,complete:boolean){
  const w=canvas.clientWidth,h=canvas.clientHeight;if(w!==lastW||h!==lastH){renderer.setSize(w,h,false);camera.aspect=w/h;lastW=w;lastH=h}
  const pose=positions[shot],smooth=p*p*(3-2*p);camera.position.lerpVectors(new T.Vector3(...pose.from),new T.Vector3(...pose.to),smooth);if(shot==='corridor'){camera.position.y+=Math.sin(time*8)*.022;camera.position.x+=Math.sin(time*4)*.014;}camera.fov=pose.fov||52;camera.updateProjectionMatrix();camera.lookAt(new T.Vector3(...pose.look));
  torso.scale.y=.2+Math.sin(time*1.3)*.003;glove.visible=ecg;glove.position.set(cx+.24+Math.sin(time*.7)*.025,1.18,cz+.27);glove.rotation.z=-.2;
  if(time-lastTime>.1||time<lastTime){drawTrace(time,complete);lastTime=time}renderer.render(scene,camera);
  const marker=wristTarget.clone().project(camera);return{x:(marker.x*.5+.5)*100,y:(-.5*marker.y+.5)*100};
 }};
}
