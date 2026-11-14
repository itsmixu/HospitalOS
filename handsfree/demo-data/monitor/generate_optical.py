"""Frozen synthetic optical transforms. Not real-camera performance evidence.
Pillow are offline fixture tools only. Decoder receives only the PNG pixels.
"""
from generate import render, ROOT
from PIL import Image,ImageDraw,ImageFilter
import random, hashlib,json,io,math
SEEDS={'development':113708,'heldout':672941}
KINDS=['blur','glare','crop','missing-label','ambiguous','erased-digit','unsupported','too-small']
manifest=[]
for split,seed in SEEDS.items():
    rng=random.Random(seed);out=ROOT/('optical-'+split);out.mkdir(exist_ok=True)
    kinds=['clear']*(36 if split=='development' else 40)+KINDS*(3 if split=='development' else 10)
    for n,kind in enumerate(kinds):
        values=[rng.randrange(0,1000) for _ in range(3)];panel=render(values,rng);draw=ImageDraw.Draw(panel)
        if kind=='blur':panel=panel.filter(ImageFilter.GaussianBlur(rng.uniform(2.0,3.2)))
        elif kind=='glare':draw.ellipse((275,80,315,128),fill=255)
        elif kind=='crop':draw.rectangle((0,0,22,360),fill=235)
        elif kind=='missing-label':draw.rectangle((32,92,120,111),fill=235)
        elif kind=='ambiguous':draw.rectangle((285,102,304,106),fill=130)
        elif kind=='erased-digit':draw.rectangle((190,72,223,135),fill=235)
        elif kind=='unsupported':draw.rectangle((32,30,160,49),fill=235)
        scale=rng.uniform(1.25,1.65) if kind!='too-small' else .5
        theta=math.radians(rng.uniform(-12,12));cx=rng.uniform(455,505);cy=rng.uniform(340,380)
        quad=[]
        for x,y in [(-240,-180),(240,-180),(240,180),(-240,180)]:
            quad.append((cx+scale*(x*math.cos(theta)-y*math.sin(theta))+rng.uniform(-14,14),cy+scale*(x*math.sin(theta)+y*math.cos(theta))+rng.uniform(-14,14)))
        # Solve image-output -> panel-input projective transform, independently of Kotlin geometry.
        A=[];B=[]
        for (x,y),(u,v) in zip(quad,[(0,0),(480,0),(480,360),(0,360)]):
            A.extend([[x,y,1,0,0,0,-u*x,-u*y],[0,0,0,x,y,1,-v*x,-v*y]]);B.extend([u,v])
        matrix=[list(map(float,row))+[float(v)] for row,v in zip(A,B)]
        for k in range(8):
            pivot=max(range(k,8),key=lambda r:abs(matrix[r][k]));matrix[k],matrix[pivot]=matrix[pivot],matrix[k]
            divisor=matrix[k][k];matrix[k]=[v/divisor for v in matrix[k]]
            for row in range(8):
                if row!=k:
                    multiple=matrix[row][k];matrix[row]=[a-multiple*b for a,b in zip(matrix[row],matrix[k])]
        coeff=[row[-1] for row in matrix]
        im=panel.transform((960,720),Image.Transform.PERSPECTIVE,coeff,Image.Resampling.BICUBIC,fillcolor=35)
        # Camera JPEG + subpixel resampling; exposure varies without inventing missing strokes.
        buf=io.BytesIO();im.save(buf,format='JPEG',quality=rng.randint(88,97));buf.seek(0);im=Image.open(buf).convert('L')
        gain=rng.uniform(.82,1.02);offset=rng.randint(-5,8);im=im.point(lambda v:max(0,min(255,round(v*gain+offset))))
        name=hashlib.sha256(f'optical:{seed}:{n}'.encode()).hexdigest()[:16]+'.png';im.save(out/name)
        manifest.append({'split':split,'file':f'optical-{split}/{name}','kind':kind,'values':values,'sha256':hashlib.sha256((out/name).read_bytes()).hexdigest()})
(ROOT/'optical-manifest.json').write_text(json.dumps({'seeds':SEEDS,'cases':manifest},indent=2)+'\n')
(ROOT/'optical-cases.tsv').write_text('\n'.join('\t'.join([c['split'],c['file'],c['kind'],*[str(v) for v in c['values']]]) for c in manifest)+'\n')
print(f'Rendered {len(manifest)} frozen optical scenes; heldout not evaluated during development.')
