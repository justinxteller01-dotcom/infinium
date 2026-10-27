import sys, numpy as np
from PIL import Image, ImageSequence
for p in sys.argv[1:]:
    im=Image.open(p); fr=[np.asarray(f.convert('RGB')).astype(int) for f in ImageSequence.Iterator(im)]
    dur=[f.info.get('duration') for f in ImageSequence.Iterator(Image.open(p))]
    st=[np.abs(fr[i]-fr[i+1]).sum() for i in range(len(fr)-1)]; w=np.abs(fr[-1]-fr[0]).sum()
    print(f'{p}: n={len(fr)} durations={sorted(set(dur))} wrap/mean={w/np.mean(st):.2f} wrap/p90={w/np.percentile(st,90):.2f}')
