"""Preview PNGs per armor style (front/back/glide-off views + wing front/back texture faces) and the symmetry check."""
import os, sys, shutil
from PIL import Image
import render3d_lib as L
E=os.path.abspath('src/main/resources/assets/infinium/textures/entity')+'/'
OUT='previews/'; os.makedirs(OUT,exist_ok=True)
FR={'astral':40,'cybernetic':40,'singularity':100,'cyberpsycho':80}
bad_total=0
for st,F in FR.items():
    root=f'/tmp/texroot_{st}/'; shutil.rmtree(root,ignore_errors=True); os.makedirs(root+'entity/infinium_glow')
    os.symlink(E+'equipment',root+'entity/equipment')
    for layer in ('humanoid','humanoid_leggings','wings'): os.symlink(E+f'infinium_glow/{st}/{layer}',root+f'entity/infinium_glow/{layer}')
    L._TC.clear(); L.FRAMES_HINT[0]=F
    L.render_views(root,0,OUT+f'{st}_views.png',size=260)
    L._TC.clear()
    sym=L.view_symmetry(root,4,size=160,scale=4); bad=sum(v for k,v in sym.items()); bad_total+=bad
    # wing texture faces, 8 frames side by side, x12
    faces=[]
    for nm,x0 in (('front',24),('back',36)):
        strip=Image.new('RGBA',(8*10*12+7*6,20*12),(30,28,36,255))
        for i in range(8):
            f=i*F//8; w=Image.open(E+f'equipment/wings/infinium.png').convert('RGBA'); g=Image.open(E+f'infinium_glow/{st}/wings/{f}.png').convert('RGBA'); w.alpha_composite(g)
            strip.paste(w.crop((x0,2,x0+10,22)).resize((120,240),Image.NEAREST),(i*126,0))
        strip.save(OUT+f'{st}_wings_{nm}.png')
    print(f'{st:12s} views + wings front/back written; symmetry mismatches (4 frames, all views): {sym}')
print('SYMMETRY TOTAL', bad_total)
# helmet face-perimeter glow sheets (32x16 HelmetExtras texture: side-bar texels at u0-3, brow u4-17, shard u12-25 v9-13)
H=E+'infinium_helmet_extra/'
for nm in ('glow','glow_cybernetic','glow_cyberpsycho'):
    strip=Image.new('RGBA',(8*(32*8+8),16*8),(30,28,36,255))
    for i in range(8):
        g=Image.open(H+f'{nm}/{i*5}.png').convert('RGBA'); b=Image.open(H+'base.png').convert('RGBA'); b.alpha_composite(g)
        strip.paste(b.resize((256,128),Image.NEAREST),(i*264,0))
    strip.save(OUT+f'helmet_face_{nm}.png')
print('helmet face sheets written')
# wing absorb strip (CyberPsycho, glide OFF, back view): t = 1 -> 0 over 0.7 s, same scaling as ElytraModelMixin,
# with the inward-pulled style dust (ArmorParticles.wingFx) projected on top.
import math, random
from PIL import ImageDraw
def smooth(t): t=max(0,min(1,t)); return t*t*(3-2*t)
st='cyberpsycho'; root=f'/tmp/texroot_{st}/'; L.T=root+'entity/'; L._TC.clear(); L.FRAMES_HINT[0]=80
N=8; S=260; strip=Image.new('RGBA',(N*S,S+24),(20,18,26,255)); rnd=random.Random(3)
COLS=[(0xB0,0x4C,0xFF),(0xD2,0x4C,0xFF),(0xFF,0x4C,0xE0),(0xFF,0x7A,0xD9)]
for i in range(N):
    t=1-i/(N-1); k=smooth(t); ys=smooth(min(1,t*1.4)); zin=(1-k)*-1.5
    pose=dict(L.POSES['standing'],wing=(0.35,-1.05),wscale=(max(.02,k),max(.02,ys),max(.05,k)),zl=zin,zr=zin)
    if t<=0.001: pose['nowings']=True
    im=L.render(L.build(i*10%80,pose),math.pi,0.0,0.0,size=S,scale=6); d=ImageDraw.Draw(im)
    if 0<t<1 or i==N-1:
        e=1-t
        for j in range(26):
            side=rnd.choice((-1,1)); wx=side*(2+rnd.random()*10)*max(.15,k); wy=2+rnd.random()*14*max(.15,k)
            cx,cy=0,4; px=wx+(cx-wx)*e*0.6; py=wy+(cy-wy)*e*0.6
            x,y=-px,py-12                                   # back view: mirror x; centre on hips like L.render
            c=rnd.choice(COLS); sx,sy=S/2+x*6,S/2+y*6; d.rectangle((sx-2,sy-2,sx+2,sy+2),fill=c+(255,))
    strip.paste(im,(i*S,0)); ImageDraw.Draw(strip).text((i*S+6,S+6),f't={t:.2f}  ({int((1-t)*700)} ms)',fill=(220,220,220,255))
strip.save(OUT+'wings_absorb_strip.png'); print('absorb strip written')
