# Tiny software renderer (painter's algorithm, per-texel quads) of the vanilla player + armor + elytra boxes
# with the Infinium textures. Preview only; geometry follows vanilla HumanoidModel/ElytraModel box layouts.
import math, os
from PIL import Image, ImageDraw
T='src/main/resources/assets/infinium/textures/entity/'
SKIN=Image.open('ref/mc/assets/minecraft/textures/entity/player/wide/steve.png').convert('RGBA')
F=48; PV='preview/'
def comp(a,b): c=a.copy(); c.alpha_composite(b); return c
def tex(layer,f):
    return comp(Image.open(T+f'equipment/{layer}/infinium.png').convert('RGBA'), Image.open(T+f'infinium_glow/{layer}/{f}.png').convert('RGBA'))

def rot(p,rx,ry,rz):
    x,y,z=p
    # MC applies Z, then Y, then X (PoseStack: rotateZYX)
    c,s=math.cos(rz),math.sin(rz); x,y=x*c-y*s,x*s+y*c
    c,s=math.cos(ry),math.sin(ry); x,z=x*c+z*s,-x*s+z*c
    c,s=math.cos(rx),math.sin(rx); y,z=y*c-z*s,y*s+z*c
    return (x,y,z)

def box_quads(img,u,v,x0,y0,z0,w,h,d,g=0.0,mirror=False):
    """Yield (corners[4], rgba) per texel for a vanilla-UV box, inflated by g."""
    X0,Y0,Z0=x0-g,y0-g,z0-g; sx,sy,sz=(w+2*g)/w,(h+2*g)/h,(d+2*g)/d
    px=img.load(); out=[]
    def face(fu,fv,fw,fh,mapf):
        for j in range(fh):
            for i in range(fw):
                c=px[fu+i,fv+j]
                if c[3]<20: continue
                ii=(fw-1-i) if mirror else i
                cs=[mapf(ii+a,j+b) for a,b in((0,0),(1,0),(1,1),(0,1))]
                out.append((cs,c))
    face(u+d,v+d,w,h,lambda a,b:(X0+a*sx,Y0+b*sy,Z0))                       # front (-z)
    face(u+2*d+w,v+d,w,h,lambda a,b:(X0+(w-a)*sx,Y0+b*sy,Z0+d*sz))           # back (+z)
    face(u,v+d,d,h,lambda a,b:(X0,Y0+b*sy,Z0+(d-a)*sz))                      # right (-x)
    face(u+d+w,v+d,d,h,lambda a,b:(X0+w*sx,Y0+b*sy,Z0+a*sz))                 # left (+x)
    face(u+d,v,w,d,lambda a,b:(X0+a*sx,Y0,Z0+b*sz))                          # top
    face(u+d+w,v,w,d,lambda a,b:(X0+a*sx,Y0+h*sy,Z0+b*sz))                   # bottom
    return out

def part(quads,pivot,r=(0,0,0)):
    res=[]
    for cs,c in quads:
        res.append(([tuple(pivot[k]+rot(p,*r)[k] for k in range(3)) for p in cs],c))
    return res

def build(f,pose):
    arm=tex('humanoid',f); leg=tex('humanoid_leggings',f); wing=tex('wings',f)
    Q=[]
    la=pose.get('arm',0.0)
    def limbset(img,g,parts):
        for (u,v,x0,y0,z0,w,h,d,pv,r,m) in parts: Q.extend(part(box_quads(img,u,v,x0,y0,z0,w,h,d,g,m),pv,r))
    body=[(0,0,-4,-8,-4,8,8,8,(0,0,0),(0,0,0),False),(16,16,-4,0,-2,8,12,4,(0,0,0),(0,0,0),False),
          (40,16,-3,-2,-2,4,12,4,(-5,2,0),(0,0,la),False),(40,16,-1,-2,-2,4,12,4,(5,2,0),(0,0,-la),True),
          (0,16,-2,0,-2,4,12,4,(-1.9,12,0),(0,0,0),False),(0,16,-2,0,-2,4,12,4,(1.9,12,0),(0,0,0),True)]
    limbset(SKIN,0.0,body)                                             # steve underneath
    limbset(arm,1.0,[p for k,p in enumerate(body) if k in(0,1,2,3,4,5)])   # helmet/chest/arms/boots layer
    limbset(leg,0.5,[body[1],body[4],body[5]])                        # leggings layer
    # elytra (64x32, wing box uv 22,0 size 10x20x2); WingsLayer translates +2px in z
    rx,rz=pose['wing']
    lw=part(box_quads(wing,22,0,-10,0,0,10,20,2,0.0,False),(5,0,2),(rx,0,rz))
    rw=part(box_quads(wing,22,0,0,0,0,10,20,2,0.0,True),(-5,0,2),(rx,0,-rz))
    Q.extend(lw); Q.extend(rw)
    return Q

def render(Q,yaw,pitch,body_pitch=0.0,size=420,scale=9.0):
    img=Image.new('RGBA',(size,size),(46,44,52,255)); dr=ImageDraw.Draw(img)
    polys=[]
    for cs,c in Q:
        pts=[]
        for p in cs:
            x,y,z=p; y-=12  # centre on hips
            x,y,z=rot((x,y,z),body_pitch,0,0)            # whole-body pitch (gliding)
            x,y,z=rot((x,y,z),0,yaw,0); x,y,z=rot((x,y,z),pitch,0,0)
            pts.append((x,y,z))
        # backface cull (screen-space winding)
        (ax,ay,_),(bx,by,_),(cx,cy,_)=pts[0],pts[1],pts[2]
        polys.append((sum(p[2] for p in pts)/4,[(size/2+p[0]*scale,size/2+p[1]*scale) for p in pts],c))
    polys.sort(key=lambda t:-t[0])   # far (large z) first; viewer looks along -z... (+z is away)
    for z,pts,c in polys: dr.polygon(pts,fill=c[:3]+(255,) if c[3]>200 else c)
    return img

POSES={'standing':{'wing':(0.35,-1.05),'arm':0.0,'bp':0.0},'gliding':{'wing':(0.35,-math.pi/2+0.05),'arm':0.3,'bp':math.pi/2-0.25}}
VIEWS={'back':(math.pi,0.25),'three_quarter':(math.pi*0.78,0.35)}
os.makedirs(PV,exist_ok=True)
sheet=Image.new('RGBA',(420*4,420),(0,0,0,255)); k=0
gif=[]
for f in range(F):
    row=Image.new('RGBA',(420*4,420))
    i=0
    for pn,pose in POSES.items():
        Q=build(f,pose)
        for vn,(yaw,pitch) in VIEWS.items():
            im=render(Q,yaw,pitch+(0.6 if pn=='gliding' else 0.0),pose['bp']); row.paste(im,(i*420,0))
            if f==0: im.save(PV+f'render3d_{pn}_{vn}.png')
            i+=1
    if f==0: row.save(PV+'render3d_all_views.png')
    gif.append(row.convert('RGB').resize((420*2,210)))
gif[0].save(PV+'render3d_animated.gif',save_all=True,append_images=gif[1:],duration=100,loop=0)
print('render ok')
