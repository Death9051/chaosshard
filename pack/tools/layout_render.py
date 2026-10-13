import json,re,sys
from PIL import Image, ImageDraw
d=json.load(open(sys.argv[1]))
Q={q['questID:3']:q for q in d['questDatabase:9'].values()}
out=[]
for l in d['questLines:9'].values():
    nm=re.sub('§.','',l['properties:10']['betterquesting:10']['name:8'])
    pos={e['id:3']:(e['x:3'],e['y:3'],e['sizeX:3']) for e in l['quests:9'].values()}
    xs=[p[0] for p in pos.values()]; ys=[p[1] for p in pos.values()]
    x0,y0=min(xs)-30,min(ys)-30; W=max(xs)-x0+60; H=max(ys)-y0+60
    sc=min(1.0, 1400/W)
    im=Image.new('RGB',(int(W*sc),int(H*sc)+20),(20,16,28)); dr=ImageDraw.Draw(im)
    edges=[(p,i) for i in pos for p in Q[i]['preRequisites:11'] if p in pos]
    def c(i): x,y,s=pos[i]; return ((x-x0+s/2)*sc,(y-y0+s/2)*sc)
    for a,b in edges: dr.line([c(a),c(b)],fill=(220,180,80),width=2)
    for i,(x,y,s) in pos.items():
        cx,cy=c(i); r=s/2*sc; main=Q[i]['properties:10']['betterquesting:10']['ismain:1']
        dr.rectangle([cx-r,cy-r,cx+r,cy+r],fill=(90,50,140) if main else (60,60,80),outline=(240,220,160))
    def cross(e1,e2):
        (a,b),(cc,dd)=e1,e2
        if len({a,b,cc,dd})<4: return False
        A,B,C,D=[c(z) for z in (a,b,cc,dd)]
        o=lambda p,q,r:(q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0])
        return o(A,B,C)*o(A,B,D)<0 and o(C,D,A)*o(C,D,B)<0
    cr=sum(cross(edges[i],edges[j]) for i in range(len(edges)) for j in range(i+1,len(edges)))
    dr.text((6,int(H*sc)+4),f'{nm}: {cr}',fill=(240,240,240))
    out.append(im); print(nm, cr)
Wt=max(i.width for i in out); Ht=sum(i.height+10 for i in out)
sheet=Image.new('RGB',(Wt,Ht),(40,40,50)); y=0
for i in out: sheet.paste(i,(0,y)); y+=i.height+10
sheet.save(sys.argv[2])
