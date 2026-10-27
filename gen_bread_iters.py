# 10 bread-armor style iterations -> preview/bread/iter/bNN/ (does NOT touch src/ textures)
import math, os, random
from PIL import Image, ImageDraw
import render3d_lib as R
MC='ref/mc/assets/minecraft/textures/'; OUTD='preview/bread/iter/'
B=[(63,46,14),(76,56,17),(87,65,20),(101,75,23),(140,102,30),(162,121,36),(188,137,39)]
CRUMB=(232,205,150); CRUMB2=(214,182,120); TOAST=(201,150,80); TOAST2=(170,118,52)
LOAF=Image.open(MC+'item/bread.png').convert('RGBA'); LOAF8=LOAF.resize((8,8),Image.NEAREST)
BOX={'head':(0,0,8,8,8),'hat':(32,0,8,8,8),'body':(16,16,8,12,4),'arm':(40,16,4,12,4),'leg':(0,16,4,12,4)}
def faces(u,v,w,h,d):
    return {'top':(u+d,v,w,d),'bottom':(u+d+w,v,w,d),'right':(u,v+d,d,h),'front':(u+d,v+d,w,h),'left':(u+d+w,v+d,d,h),'back':(u+2*d+w,v+d,w,h)}
def px(im,x,y,c):
    if 0<=x<im.width and 0<=y<im.height: im.putpixel((x,y),c+(255,) if len(c)==3 else c)
def rect(im,x,y,w,h,c):
    for j in range(h):
        for i in range(w): px(im,x+i,y+j,c)
def stamp(im,spr,x,y,clip):
    cx,cy,cw,ch=clip; t=Image.new('RGBA',im.size,(0,0,0,0)); t.alpha_composite(spr,(0,0) if False else (0,0)) if False else None
    for j in range(spr.height):
        for i in range(spr.width):
            p=spr.getpixel((i,j))
            if p[3] and cx<=x+i<cx+cw and cy<=y+j<cy+ch: px(im,x+i,y+j,p[:3])
# ---------- motif helpers ----------
def loaf_blob(im,x,y,w,h,base=5,score='diag',n=None):
    """rounded loaf: dark rim, golden body, highlight top, scored lines"""
    for j in range(h):
        for i in range(w):
            corner=(i in(0,w-1)) and (j in(0,h-1))
            if corner and w>3 and h>3: continue
            edge=i in(0,w-1) or j in(0,h-1)
            k=3 if edge else (6 if j<=h//3 else base)
            if not edge and score=='diag' and (i+j)%4==0: k=4
            if not edge and score=='horiz' and j%3==0 and 0<i<w-1: k=4
            if not edge and score=='vert' and i%3==0: k=4
            px(im,x+i,y+j,B[k])
def slice_tile(im,x,y,w,h):
    for j in range(h):
        for i in range(w):
            edge=i in(0,w-1) or j in(0,h-1)
            c=B[4] if edge else (CRUMB2 if (i*7+j*3)%9==0 else CRUMB)
            if j==0 and edge: c=B[3]
            px(im,x+i,y+j,c)
def baguette_v(im,x,y,w,h):
    for j in range(h):
        for i in range(w):
            k=3 if i in(0,w-1) else (6 if i==1 else 5)
            if 0<i<w-1 and (j+i)%4==0: k=4 if (j%4==0) else k
            if 0<i<w-1 and (j-i)%4==0: k=CRUMB2 and 7
            c=B[k] if k<7 else CRUMB2
            px(im,x+i,y+j,c)
def baguette_h(im,x,y,w,h):
    t=Image.new('RGBA',(h,w)); baguette_v(t,0,0,h,w)
    t=t.transpose(Image.ROTATE_90); stamp(im,t,x,y,(x,y,w,h))
def braid(im,x,y,w,h):
    for j in range(h):
        for i in range(w):
            a=(i+ (j//3)%2*2)%4; b=j%3
            if b==2 or a==3: c=B[2]
            elif b==0 and a in(0,1): c=B[6] if a==0 else CRUMB2
            else: c=B[5]
            px(im,x+i,y+j,c)
def rolls(im,x,y,w,h):
    rect(im,x,y,w,h,B[2])
    for j in range(0,h,4):
        for i in range(-(j//4%2)*2,w,4):
            for a,b,c in((1,0,B[5]),(2,0,B[5]),(0,1,B[4]),(1,1,B[6]),(2,1,B[6]),(3,1,B[4]),(0,2,B[4]),(1,2,B[5]),(2,2,B[5]),(3,2,B[3]),(1,3,B[3]),(2,3,B[3])):
                if 0<=i+a<w and 0<=j+b<h: px(im,x+i+a,y+j+b,c)
def herring(im,x,y,w,h,spr,step):
    rect(im,x,y,w,h,B[0]); m=spr.transpose(Image.FLIP_LEFT_RIGHT); s=spr.width
    for jj,yy in enumerate(range(-s,h+s,step)):
        for ii,xx in enumerate(range(-s-(jj%2)*step//2,w+s,step)):
            stamp(im,(spr,m)[ii%2],x+xx,y+yy,(x,y,w,h))
def scored(im,x,y,w,h):
    for j in range(h):
        for i in range(w):
            k=6 if j%6<2 else 5
            if (i+j)%6==0 or (i-j)%6==0: k=3
            elif (i+j)%6==1 or (i-j)%6==5: k=CRUMB2 and 7
            px(im,x+i,y+j,B[k] if k<7 else CRUMB2)
def toast(im,x,y,w,h):
    for j in range(h):
        for i in range(w):
            edge=i in(0,w-1) or j in(0,h-1)
            c=TOAST2 if edge else (TOAST2 if (i*5+j*11)%13==0 else TOAST)
            if j==0 and 1<=i<w-1: c=B[3]
            px(im,x+i,y+j,c)
    if w>=6 and h>=6:   # rounded top corners like a slice
        px(im,x,y,(0,0,0)); px(im,x+w-1,y,(0,0,0))
# ---------- styles: paint(img,part,face,(x,y,w,h)) ----------
def s01(im,p,f,r): herring(im,*r,LOAF,11)
def s02(im,p,f,r):
    x,y,w,h=r; rect(im,x,y,w,h,B[2])
    for j in range(0,h,5):
        for i in range(-(j//5%2)*2,w,5): slice_tile(im,x+i,y+j,5,5) if True else None
    rect(im,x,y,w,h,B[2]) if False else None
def s03(im,p,f,r):
    x,y,w,h=r
    if p in('arm','leg'): [baguette_v(im,x+i,y,min(4,w-i),h) for i in range(0,w,4)]
    else: [baguette_h(im,x,y+j,w,min(4,h-j)) for j in range(0,h,4)]
def s04(im,p,f,r):
    x,y,w,h=r
    if p in('head','hat'): loaf_blob(im,x,y,w,h,5,'diag' if f!='top' else 'horiz')
    else: herring(im,*r,LOAF8,6)
def s05(im,p,f,r):
    x,y,w,h=r
    if p=='body' and f in('front','back'): rect(im,x,y,w,h,B[3]); toast(im,x,y,w,h//2+1); toast(im,x,y+h//2,w,h-h//2)
    else: scored(im,*r)
def s06(im,p,f,r): scored(im,*r)
def s07(im,p,f,r): braid(im,*r)
def s08(im,p,f,r):
    x,y,w,h=r
    if p in('head','hat'):
        loaf_blob(im,x,y,w,h,5,'vert')
        if f=='top': rect(im,x+1,y+1,w-2,h-2,CRUMB); [px(im,x+1+(i*3)%(w-2),y+1+(i*5)%(h-2),CRUMB2) for i in range(6)]
        else: rect(im,x,y,w,2,CRUMB); rect(im,x,y+1,w,1,CRUMB2)
    else: s02(im,p,f,r)
def s09(im,p,f,r):
    {'head':s04,'hat':s04,'body':s05,'arm':s03,'leg':lambda im,p,f,r:rolls(im,*r)}[p](im,p,f,r)
def s10(im,p,f,r):
    x,y,w,h=r; loaf_blob(im,x,y,w,h,5,'none')
    if w>=4 and h>=6:
        for j in range(2,h-2,4):
            for i in range(1,w-1): px(im,x+i,y+j+(i*2//w),B[3]); px(im,x+i,y+j+1+(i*2//w),CRUMB2)
STY=[('b01','1:1 vanilla loaves, whole, herringbone',s01),('b02','sliced bread grid: crust rim + pale crumb',s02),
 ('b03','baguettes: vertical on limbs, horizontal on head/body',s03),('b04','loaf-shaped helmet + small loaves elsewhere',s04),
 ('b05','toast slices on chest + scored crust elsewhere',s05),('b06','scored diamond-crust surface everywhere',s06),
 ('b07','braided challah weave',s07),('b08','bread-bowl helmet (crumb rim) + sliced-bread body',s08),
 ('b09','mixed: loaf helm, toast chest, baguette arms, roll legs',s09),('b10','one big scored loaf per face',s10)]
def apply_mask(fill,mask,outline=False):
    m=mask.convert('RGBA'); W,H=m.size; on=lambda x,y:0<=x<W and 0<=y<H and m.getpixel((x,y))[3]>0
    o=Image.new('RGBA',(W,H),(0,0,0,0))
    for y in range(H):
        for x in range(W):
            if on(x,y):
                c=fill.getpixel((x,y))[:3]
                if outline and any(not on(x+a,y+b) for a,b in((1,0),(-1,0),(0,1),(0,-1))): c=B[0]
                o.putpixel((x,y),c+(255,))
    return o
blank=Image.new('RGBA',(64,32),(0,0,0,0)); S=240
sheet_f=[]; sheet_b=[]; icons_all=[]
for name,desc,fn in STY:
    d=OUTD+name+'/'; os.makedirs(d,exist_ok=True); tex={}
    for layer in('humanoid','humanoid_leggings'):
        im=Image.new('RGBA',(64,32),B[3]+(255,))
        for p,bx in BOX.items():
            for f,r in faces(*bx).items(): fn(im,p,f,r)
        tex[layer]=apply_mask(im,Image.open(MC+f'entity/equipment/{layer}/netherite.png')); tex[layer].save(d+f'{layer}.png')
    ic=[]
    for piece,p in(('helmet','head'),('chestplate','body'),('leggings','leg'),('boots','leg')):
        im=Image.new('RGBA',(16,16),B[3]+(255,)); fn(im,p,'front',(0,0,16,16))
        a=apply_mask(im,Image.open(MC+f'item/netherite_{piece}.png'),True); a.save(d+f'bread_{piece}.png'); ic.append(a)
    sh=Image.new('RGBA',(4*136+8,144),(46,44,52,255))
    for i,a in enumerate(ic): sh.alpha_composite(a.resize((128,128),0),(8+i*136,8))
    sh.save(d+'icons.png'); icons_all.append(sh)
    R.tex=lambda l,f,t=tex: blank if l=='wings' else t[l]
    Q=R.build(0,dict(R.POSES['standing'],nowings=True))
    fr=[R.render(Q,k*math.pi/16,0.2,0.0,size=S,scale=5,shade=True).convert('RGB') for k in range(32)]
    fr[0].save(d+'spin.gif',save_all=True,append_images=fr[1:],duration=90,loop=0)
    sheet_f.append(R.render(Q,0,0,0,size=S,scale=5,shade=True)); sheet_b.append(R.render(Q,math.pi,0,0,size=S,scale=5,shade=True))
    print(name,'ok')
C=Image.new('RGBA',(S*10,S*2+30+144),(30,30,36,255)); D=ImageDraw.Draw(C)
for i in range(10):
    C.paste(sheet_f[i],(i*S,24)); C.paste(sheet_b[i],(i*S,24+S)); D.text((i*S+6,6),STY[i][0],fill='white')
    C.alpha_composite(icons_all[i].resize((S,int(144*S/552))),(i*S,24+2*S+4))
C.save(OUTD+'compare_front_back.png')
