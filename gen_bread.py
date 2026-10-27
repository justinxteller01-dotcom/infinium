# ---- BreadCraft textures: whole 1:1 vanilla bread loaves stamped/overlapped across the armor ----
from PIL import Image as _I
_MC='ref/mc/assets/minecraft/textures/'; _OUT='src/main/resources/assets/infinium/textures/'
_LOAF=_I.open(_MC+'item/bread.png').convert('RGBA')
_LOAF=_LOAF.resize((8,8),_I.NEAREST)   # half-size loaf motif (~7px diagonal)
_V=[_LOAF,_LOAF.transpose(_I.FLIP_LEFT_RIGHT),_LOAF.transpose(_I.ROTATE_90),_LOAF.transpose(_I.FLIP_TOP_BOTTOM)]
_DARK=(63,46,14,255)
def _loaves(W,H,step,seed):
    """Herringbone of whole 1:1 loaves: each row alternates the native diagonal loaf and its mirror so
    pairs meet in V's; rows shingle downward so lower loaves overlap the ends of the ones above."""
    c=_I.new('RGBA',(W+48,H+48),_DARK)               # gaps show darkest crust; 24px margin for partial loaves
    for j,y in enumerate(range(-4,H+24,step)):
        for i,x in enumerate(range(-4-(j%2)*step//2+seed,W+24,step)):
            c.alpha_composite(_V[(i+seed)%2],(x+24-4,y+24-4))
    return c.crop((24,24,24+W,24+H))
def _sym(c,x0,w):                                     # mirror the left half of a region onto its right half
    l=c.crop((x0,0,x0+w//2,c.height)); c.paste(l.transpose(_I.FLIP_LEFT_RIGHT),(x0+w-w//2,0))
def _box(m,u,v,w,h,d,rows=None):                    # vanilla box-UV footprint; rows=(a,b) limits side faces
    from PIL import ImageDraw; D=ImageDraw.Draw(m)
    a,b=rows or (0,h)
    if a==0: D.rectangle((u+d,v,u+d+2*w-1,v+d-1),fill=255)
    if b==h: D.rectangle((u+d+w,v,u+d+2*w-1,v+d-1),fill=255)
    if a>0 and b==h: D.rectangle((u+d+w,v,u+d+2*w-1,v+d-1),fill=255)
    D.rectangle((u,v+d+a,u+2*(w+d)-1,v+d+b-1),fill=255)
def _mask(layer):
    m=_I.new('L',(64,32),0)
    if layer=='humanoid':
        _box(m,0,0,8,8,8); _box(m,16,16,8,12,4); _box(m,40,16,4,12,4); _box(m,0,16,4,12,4,(7,12))
    else:
        _box(m,16,16,8,12,4,(0,5)); _box(m,0,16,4,12,4,(0,9))
    return m
def _apply(mask,fill,outline):
    mp=mask.load() if mask.mode=='L' else None
    if mp is None: mm=mask.convert('RGBA'); mp2=mm.load(); W,H=mm.size; on=lambda x,y:0<=x<W and 0<=y<H and mp2[x,y][3]>0
    else: W,H=mask.size; on=lambda x,y:0<=x<W and 0<=y<H and mp[x,y]>0
    fp=fill.load(); o=_I.new('RGBA',(W,H),(0,0,0,0)); op=o.load()
    for y in range(H):
        for x in range(W):
            if not on(x,y): continue
            op[x,y]=fp[x,y]
            if outline and any(not on(x+a,y+b) for a,b in((1,0),(-1,0),(0,1),(0,-1))): op[x,y]=_DARK
    return o
# User-picked design b04 (gen_bread_iters.py -> preview/bread/iter/b04): ship those files verbatim.
import shutil as _sh
_B04='preview/bread/iter/b04/'
for _l in ['humanoid','humanoid_leggings']: _sh.copyfile(_B04+f'{_l}.png',_OUT+f'entity/equipment/{_l}/bread.png')
for _p in ['helmet','chestplate','leggings','boots']: _sh.copyfile(_B04+f'bread_{_p}.png',_OUT+f'item/bread_{_p}.png')
# ---- BreadCraft (exec'd from gen_data.py) ----
for x in armor:
    n='bread_'+x
    w(f'assets/infinium/items/{n}.json',{"model":{"type":"minecraft:model","model":f"infinium:item/{n}"}})
    w(f'assets/infinium/models/item/{n}.json',{"parent":"minecraft:item/generated","textures":{"layer0":f"infinium:item/{n}"}})
    lang[f"item.infinium.{n}"]="Bread "+x.capitalize()
w('assets/infinium/lang/en_us.json',lang)
w('assets/infinium/equipment/bread.json',{"layers":{"humanoid":[{"texture":"infinium:bread"}],"humanoid_leggings":[{"texture":"infinium:bread"}]}})
shapes={'helmet':["BBB","B B"],'chestplate':["B B","BBB","BBB"],'leggings':["BBB","B B","B B"],'boots':["B B","B B"]}
for x,p in shapes.items():
    w(f'data/infinium/recipe/bread_{x}.json',{"type":"minecraft:crafting_shaped","category":"equipment","key":{"B":"minecraft:bread"},"pattern":p,"result":{"id":f"infinium:bread_{x}","count":1}})
w('data/infinium/tags/item/bread_repair.json',{"values":["minecraft:bread"]})
for t,x in {'head_armor':'helmet','chest_armor':'chestplate','leg_armor':'leggings','foot_armor':'boots'}.items():
    w(f'data/minecraft/tags/item/{t}.json',{"replace":False,"values":['infinium:infinium_'+x,'infinium:bread_'+x]})
# ---- Infinium Rocket (R5: renamed; registry id infinium:infinium_rocket, alias from netherite_rocket; smithing, no template) ----
for _old in ('assets/infinium/items/netherite_rocket.json','assets/infinium/models/item/netherite_rocket.json','data/infinium/recipe/netherite_rocket.json','assets/infinium/textures/item/netherite_rocket.png'):
    if os.path.exists(R+_old): os.remove(R+_old)
w('assets/infinium/items/infinium_rocket.json',{"model":{"type":"minecraft:model","model":"infinium:item/infinium_rocket"}})
w('assets/infinium/models/item/infinium_rocket.json',{"parent":"minecraft:item/generated","textures":{"layer0":"infinium:item/infinium_rocket"}})
import base64 as _b64
os.makedirs(R+'assets/infinium/textures/item',exist_ok=True)
open(R+'assets/infinium/textures/item/infinium_rocket.png','wb').write(_b64.b64decode('iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAtUlEQVR42mNgGPTgnNmR//jkmfBJrjB59F9OTQivITgNqFHZ9J+BgYHh0a13DLf+ycH56IAFlwFXPh2BMAQMGK58uIDgkwLEBMT+B4iV/RcTEPtPVhg42TgwfFA4wSAiJEJeIEqIiTEwMDAwvHn3hjwDeA65MWT8W8JgxZaAUw0LPgPuaCxhOPKqh+EVzxsGhlcD4YUXryDWIgeiiZzIfxM5kf9ER6ODmR08GonWiA5IspVUAABsgj+bHB8KpQAAAABJRU5ErkJggg=='))
lang["item.infinium.booster_infinium_rocket"]="Infinium Rocket"
w('assets/infinium/lang/en_us.json',lang)
w('data/infinium/recipe/infinium_rocket_smithing.json',{"type":"minecraft:smithing_transform","base":"minecraft:firework_rocket",
  "addition":"infinium:infinium_ingot","result":{"id":"infinium:infinium_rocket","count":1}})
