# Recolors vanilla netherite textures (from the local game jar) into Infinium dark purple/black. Local use only (Mojang assets).
import colorsys, math, os, json
from PIL import Image
MC='ref/mc/assets/minecraft/textures/'
OUT='src/main/resources/assets/infinium/textures/'
STOPS=[(0.0,(4,1,7)),(0.35,(22,6,34)),(0.6,(48,14,74)),(0.85,(84,32,128)),(1.0,(122,58,176))]
GLOW=(190,90,255)
def grad(t):
    t=max(0,min(1,t))
    for (a,ca),(b,cb) in zip(STOPS,STOPS[1:]):
        if t<=b:
            f=(t-a)/(b-a); return tuple(int(ca[i]+(cb[i]-ca[i])*f) for i in range(3))
    return STOPS[-1][1]
def is_handle(r,g,b):
    h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
    return s>0.38 and 0.03<h<0.15 and v>0.2
def recolor(img, keep_handles=False):
    img=img.convert('RGBA'); px=img.load(); W,H=img.size
    lums=[0.299*px[x,y][0]+0.587*px[x,y][1]+0.114*px[x,y][2] for y in range(H) for x in range(W) if px[x,y][3]>0]
    lo,hi=min(lums),max(lums)
    out=Image.new('RGBA',img.size); op=out.load(); glowmask={}
    for y in range(H):
        for x in range(W):
            r,g,b,a=px[x,y]
            if a==0: op[x,y]=(0,0,0,0); continue
            if keep_handles and is_handle(r,g,b): op[x,y]=(r,g,b,a); continue
            t=((0.299*r+0.587*g+0.114*b)-lo)/max(1,hi-lo)
            op[x,y]=grad(t)+(a,)
            if t>0.78: glowmask[(x,y)]=(t-0.78)/0.22
    return out,glowmask
def animate(base,mask,frames=8):
    W,H=base.size; sheet=Image.new('RGBA',(W,H*frames)); bp=base.load()
    for f in range(frames):
        k=0.5-0.5*math.cos(2*math.pi*f/frames)
        fr=base.copy(); fp=fr.load()
        for (x,y),m in mask.items():
            r,g,b,a=bp[x,y]; w=min(1,k*(0.5+0.5*m))
            fp[x,y]=(int(r+(GLOW[0]-r)*w),int(g+(GLOW[1]-g)*w),int(b+(GLOW[2]-b)*w),a)
        sheet.paste(fr,(0,H*f))
    return sheet
def save_anim(img,path):
    os.makedirs(os.path.dirname(path),exist_ok=True); img.save(path)
    json.dump({"animation":{"frametime":3,"interpolate":True}},open(path+'.mcmeta','w'))
items={'infinium_ingot':'netherite_ingot'}
for p in ['helmet','chestplate','leggings','boots','sword','pickaxe','axe','shovel','hoe']: items['infinium_'+p]='netherite_'+p
for n,src in items.items():
    base,mask=recolor(Image.open(MC+'item/'+src+'.png'),keep_handles=src.split('_')[-1] in('sword','pickaxe','axe','shovel','hoe'))
    save_anim(animate(base,mask),OUT+'item/'+n+'.png')
for layer in ['humanoid','humanoid_leggings']:
    base,_=recolor(Image.open(MC+f'entity/equipment/{layer}/netherite.png'))
    os.makedirs(OUT+f'entity/equipment/{layer}',exist_ok=True); base.save(OUT+f'entity/equipment/{layer}/infinium.png')
base,_=recolor(Image.open(MC+'entity/equipment/wings/elytra.png'))
os.makedirs(OUT+'entity/equipment/wings',exist_ok=True); base.save(OUT+'entity/equipment/wings/infinium.png')
# farmland: moist farmland with subtle purple tint
os.makedirs(OUT+'block',exist_ok=True)
fm=Image.open(MC+'block/farmland_moist.png').convert('RGBA'); p=fm.load()
for y in range(fm.size[1]):
    for x in range(fm.size[0]):
        r,g,b,a=p[x,y]; p[x,y]=(int(r*0.85+30*0.15*4) if False else int(r*0.82+0.18*70),int(g*0.8),int(b*0.8+0.2*95),a)
fm.save(OUT+'block/infinium_farmland_moist.png')
# bleeding effect icon 18x18: dark red drop
ic=Image.new('RGBA',(18,18),(0,0,0,0)); q=ic.load()
for y in range(18):
    for x in range(18):
        cx,cy=8.5,11.0; r=5.0
        d=math.hypot(x-cx,y-cy)
        top = y>=2 and y<cy and abs(x-cx) <= (y-2)/(cy-2)*r
        if d<=r or top:
            shade=0.6+0.4*(1-(y/18)); q[x,y]=(int(140*shade),int(10*shade),int(16*shade),255)
q[7,9]=(220,80,80,255); q[7,10]=(200,60,60,255)
os.makedirs(OUT+'mob_effect',exist_ok=True); ic.save(OUT+'mob_effect/bleeding.png')
print('ok')
# BreadCraft textures moved to gen_bread.py
