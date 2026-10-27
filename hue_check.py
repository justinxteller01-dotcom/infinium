import sys,glob,colorsys
from PIL import Image
def blue_count(paths,lo=200,hi=262,minsat=0.15,minval=0.08):
    n=tot=0
    for p in paths:
        im=Image.open(p).convert('RGBA')
        for r,g,b,a in im.getdata():
            if a<16: continue
            h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255); tot+=1
            if s>=minsat and v>=minval and lo<=h*360<=hi: n+=1
    return n,tot
if __name__=='__main__':
    R='src/main/resources/assets/infinium/textures/'
    sets={'armor':glob.glob(R+'entity/infinium_glow/singularity/humanoid/*.png'),
          'leggings':glob.glob(R+'entity/infinium_glow/singularity/humanoid_leggings/*.png'),
          'wings':glob.glob(R+'entity/infinium_glow/singularity/wings/*.png'),
          'tools+icons':[p for p in glob.glob(R+'item/**/*.png',recursive=True) if 'singularity' in p and 'gray' not in p]}
    bad=0
    for k,v in sets.items():
        n,t=blue_count(v); bad+=n; print(f'{k:12s} files={len(v):4d} lit px={t:7d} blue(200-262deg) px={n}')
    print('TOTAL blue px', bad); sys.exit(1 if bad else 0)
