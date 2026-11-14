"""Frozen independent HOS-BP-1 renderer. PIL is evaluation tooling, not an app dependency."""
from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
import random, json, hashlib
ROOT=Path(__file__).parent
# Split seeds fixed before first evaluation. Holdout contains 40 clear + 140 adverse cases.
SEEDS={"development":161115,"heldout":918274}
FONT={"H":"101101111101101","O":"111101101101111","S":"111100111001111","B":"110101110101110","P":"110101110100100","Y":"101101010010010","D":"110101101101110","I":"111010010010111","A":"010101111101101","U":"101101101101111","L":"100100100100111","E":"111100110100111","m":"000111111101101","g":"000111101001111","b":"100100110101110","p":"000110101110100","-":"000000111000000","1":"010110010010111"}
DIGITS=["abcdef","bc","abged","abgcd","fgbc","afgcd","afgecd","abc","abcdefg","abcdfg"]
RECTS={"a":(7,2,20,5),"b":(27,8,5,21),"c":(27,35,5,21),"d":(7,57,20,5),"e":(2,35,5,21),"f":(2,8,5,21),"g":(7,30,20,5)}
def render(values,rng):
    bg=rng.randint(215,250);fg=rng.randint(10,45)
    im=Image.new("L",(480,360),bg);d=ImageDraw.Draw(im)
    def rect(x,y,w,h,v=fg): d.rectangle((x,y,x+w-1,y+h-1),fill=v)
    def text(s,x,y):
        for k,c in enumerate(s):
            for i,b in enumerate(FONT[c]):
                if b=="1":rect(x+k*16+i%3*4,y+i//3*4,4,4)
    for r in [(12,12,456,4),(12,344,456,4),(12,12,4,336),(464,12,4,336)]:rect(*r)
    text("HOS-BP-1",32,30)
    for row,(name,value) in enumerate(zip(["SYS","DIA","PULSE"],values)):
        y=72+row*88;text(name,32,y+20);text("bpm" if row==2 else "mmHg",350,y+20)
        for slot,c in enumerate(str(value).zfill(3)):
            if c!=" ":
                for segment in DIGITS[int(c)]:
                    x0,y0,w,h=RECTS[segment];rect(190+44*slot+x0,y+y0,w,h)
    return im
if __name__ == '__main__':
    manifest=[]
    for split,seed in SEEDS.items():
        rng=random.Random(seed);out=ROOT/(split+"-fixed-slots");out.mkdir(exist_ok=True)
        kinds=(["clear"]*80+["blur","glare","crop","missing-label","ambiguous","unsupported","erased-digit"]*10) if split=="development" else (["clear"]*40+["blur","glare","crop","missing-label","ambiguous","unsupported","erased-digit"]*20)
        for n,kind in enumerate(kinds):
            values=[rng.randrange(0,1000) for _ in range(3)]
            im=render(values,rng);d=ImageDraw.Draw(im)
            if kind=="blur":im=im.filter(ImageFilter.GaussianBlur(rng.uniform(1.6,3.5)))
            elif kind=="glare":d.ellipse((275,80,315,128),fill=255)
            elif kind=="crop":im=im.crop((rng.randrange(14,30),0,480,360)).resize((480,360))
            elif kind=="missing-label":d.rectangle((32,92,120,111),fill=235)
            elif kind=="ambiguous":d.rectangle((285,102,304,106),fill=130)
            elif kind=="erased-digit":d.rectangle((190,72,223,135),fill=235)
            elif kind=="unsupported":d.rectangle((32,30,160,49),fill=235)
            # Opaque names; expected values remain only in evaluation manifest, never extraction API.
            name=hashlib.sha256(f"{seed}:{n}".encode()).hexdigest()[:16]+".png"
            im.save(out/name)
            manifest.append({"split":split,"file":f"{split}-fixed-slots/{name}","kind":kind,"values":values,"sha256":hashlib.sha256((out/name).read_bytes()).hexdigest()})
    (ROOT/"manifest.json").write_text(json.dumps({"seeds":SEEDS,"cases":manifest},indent=2)+"\n")
    # Tab-separated oracle is consumed only by the test harness after reading pixels.
    (ROOT/"cases.tsv").write_text("\n".join("\t".join([c["split"],c["file"],c["kind"],*[str(v) for v in c["values"]]]) for c in manifest)+"\n")
    print(f"Rendered {len(manifest)} frozen scenes")
