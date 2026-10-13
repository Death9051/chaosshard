import math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops
import os
A=os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/chaosshard/textures/gui/')
FONT='/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc'

def noise(w,h,scale,seed):
    rng=np.random.default_rng(seed)
    out=np.zeros((h,w))
    amp=1.0
    for octave in range(5):
        s=max(2,int(scale/(2**octave)))
        small=rng.random((h//s+2,w//s+2))
        img=Image.fromarray((small*255).astype('uint8')).resize((w+2*s,h+2*s),Image.BICUBIC)
        out+=np.array(img)[s:s+h,s:s+w]/255*amp
        amp*=0.5
    out-=out.min(); out/=out.max()
    return out

def nebula(w,h,seed,c1,c2,strength=1.0):
    n1=noise(w,h,w//3,seed); n2=noise(w,h,w//5,seed+1)
    base=np.zeros((h,w,3))
    base+=np.array([8,7,12])
    m1=np.clip((n1-0.45)*2.2,0,1)**1.6*strength
    m2=np.clip((n2-0.55)*2.5,0,1)**1.8*strength
    base+=m1[...,None]*np.array(c1)
    base+=m2[...,None]*np.array(c2)
    return base

def stars(arr,count,seed,maxb=200):
    rng=random.Random(seed); h,w,_=arr.shape
    for _ in range(count):
        x,y=rng.randrange(w),rng.randrange(h); b=rng.random()**3*maxb+20
        arr[y,x]=np.maximum(arr[y,x],[b,b,b*1.05])
        if b>150 and 0<x<w-1 and 0<y<h-1:
            for dx,dy in((1,0),(-1,0),(0,1),(0,-1)): arr[y+dy,x+dx]=np.maximum(arr[y+dy,x+dx],[b*0.45]*3)
    return arr

def to_img(arr): return Image.fromarray(np.clip(arr,0,255).astype('uint8'),'RGB').convert('RGBA')

def glow_layer(size,draw_fn,blur,color):
    m=Image.new('L',size,0); draw_fn(ImageDraw.Draw(m))
    g=m.filter(ImageFilter.GaussianBlur(blur))
    col=Image.new('RGBA',size,color+(0,)); col.putalpha(g); return col

def chaos_star(d,cx,cy,R,r,fill=255,width=None):
    pts=[]
    for k in range(16):
        rr=R if k%2==0 else r
        a=k*math.pi/8-math.pi/2
        pts.append((cx+rr*math.cos(a),cy+rr*math.sin(a)))
    if width: d.line(pts+[pts[0]],fill=fill,width=width)
    else: d.polygon(pts,fill=fill)

def island(d,cx,cy,w,h,fill):
    top=[(cx-w/2+i*w/40, cy+random.uniform(-4,4)) for i in range(41)]
    bottom=[]
    for i in range(40,-1,-1):
        x=cx-w/2+i*w/40; t=abs(i-20)/20
        bottom.append((x, cy+h*(1-t**1.6)+random.uniform(-6,6)))
    d.polygon(top+bottom,fill=fill)

S=1024
# ---------------------------------------------------------------- главный экран: фон + заголовок
random.seed(5)
W,H=1024,512
arr=nebula(W,H,11,[90,40,150],[40,30,90],1.0)
arr=stars(arr,900,1)
bg=to_img(arr)
bg=Image.alpha_composite(bg,glow_layer((W,H),lambda d:chaos_star(d,W*0.5,H*0.32,150,55),40,(150,80,230)))
star=Image.new('RGBA',(W,H),(0,0,0,0)); sd=ImageDraw.Draw(star)
chaos_star(sd,W*0.5,H*0.32,120,42,fill=(40,30,60,200)); chaos_star(sd,W*0.5,H*0.32,120,42,fill=(170,140,220,255),width=2)
bg=Image.alpha_composite(bg,star)
# остров
isl=Image.new('RGBA',(W,H),(0,0,0,0)); idr=ImageDraw.Draw(isl)
island(idr,W*0.5,H*0.78,420,120,(6,5,9,255))
bg=Image.alpha_composite(bg,glow_layer((W,H),lambda d:d.ellipse([W*0.5-14,H*0.78-26,W*0.5+14,H*0.78+2],fill=255),18,(220,90,255)))
rim=isl.split()[3].filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(2))
rc=Image.new('RGBA',(W,H),(170,110,230,0)); rc.putalpha(rim.point(lambda v:min(255,v*3)))
bg=Image.alpha_composite(bg,rc)
bg=Image.alpha_composite(bg,isl)
bg=Image.alpha_composite(bg,glow_layer((W,H),lambda d:d.ellipse([W*0.5-6,H*0.78-16,W*0.5+6,H*0.78-4],fill=255),4,(255,190,255)))
# виньетка
yy,xx=np.mgrid[0:H,0:W]; v=1-np.clip(((xx-W/2)/(W*0.62))**2+((yy-H/2)/(H*0.75))**2,0,1)
vig=Image.fromarray((255*(1-v)**1.5).astype('uint8')); dark=Image.new('RGBA',(W,H),(0,0,0,255)); dark.putalpha(vig)
bg=Image.alpha_composite(bg,dark)

# заголовок (прозрачный)
title=Image.new('RGBA',(W,H),(0,0,0,0))
f1=ImageFont.truetype(FONT,92); f2=ImageFont.truetype(FONT,40)
def text_layer(txt,font,y,fill):
    lay=Image.new('L',(W,H),0); d=ImageDraw.Draw(lay)
    bb=d.textbbox((0,0),txt,font=font); tw=bb[2]-bb[0]
    d.text(((W-tw)/2-bb[0],y),txt,font=font,fill=255)
    return lay
t1=text_layer('ОСКОЛОК ХАОСА',f1,160,255)
t2=text_layer('· Гримуар Осколка ·',f2,320,255)
for lay,blur,col in [(t1,22,(150,70,230)),(t1,6,(200,150,255))]:
    g=lay.filter(ImageFilter.GaussianBlur(blur)); c=Image.new('RGBA',(W,H),col+(0,)); c.putalpha(g.point(lambda v:min(255,int(v*1.6)))); title=Image.alpha_composite(title,c)
# серебряный градиент букв
grad=np.zeros((H,W,4),dtype='uint8')
for y in range(H):
    t=(y-150)/130; v=int(250-120*max(0,min(1,t))+30*math.sin(t*6))
    grad[y,:,:3]=[min(255,v),min(255,v+2),min(255,v+14)]
gi=Image.fromarray(grad,'RGBA'); gi.putalpha(t1); title=Image.alpha_composite(title,gi)
edge=t1.filter(ImageFilter.FIND_EDGES); ec=Image.new('RGBA',(W,H),(60,60,80,0)); ec.putalpha(edge.point(lambda v:min(255,v*2))); title=Image.alpha_composite(title,ec)
s2=Image.new('RGBA',(W,H),(190,180,215,0)); s2.putalpha(t2); title=Image.alpha_composite(title,s2)
# звезда-эмблема над надписью
em=Image.new('L',(W,H),0); ed=ImageDraw.Draw(em); chaos_star(ed,W/2,80,58,22,fill=255)
title=Image.alpha_composite(title,glow_layer((W,H),lambda d:chaos_star(d,W/2,80,58,22),14,(170,90,255)))
ec2=Image.new('RGBA',(W,H),(225,228,240,0)); ec2.putalpha(em); title=Image.alpha_composite(title,ec2)
hole=Image.new('L',(W,H),0); ImageDraw.Draw(hole).ellipse([W/2-12,68,W/2+12,92],fill=255)
hc=Image.new('RGBA',(W,H),(120,50,200,0)); hc.putalpha(hole); title=Image.alpha_composite(title,hc)

home=Image.new('RGBA',(W,W),(0,0,0,0)); home.paste(bg,(0,0)); home.paste(title,(0,512))
home.save(A+'quest_home.png')

# ---------------------------------------------------------------- фоны глав
def chapter(name,seed,c1,c2,motif):
    random.seed(seed)
    arr=nebula(S,S,seed,c1,c2,0.55)
    arr=stars(arr,1400,seed,150)
    img=to_img(arr)
    lay=Image.new('RGBA',(S,S),(0,0,0,0)); motif(lay,ImageDraw.Draw(lay))
    img=Image.alpha_composite(img,lay)
    # общее затемнение, чтобы квесты читались
    shade=Image.new('RGBA',(S,S),(0,0,0,70)); img=Image.alpha_composite(img,shade)
    img.convert('RGB').save(A+f'quest_bg_{name}.png')

def m_act0(lay,d):
    for i,(x,y,w) in enumerate([(300,560,380),(760,300,180),(820,760,140)]):
        g=glow_layer((S,S),lambda dd:dd.ellipse([x-10,y-24,x+10,y-4],fill=255),20,(200,80,255)); lay.alpha_composite(g)
        island(d,x,y,w,w*0.3,(14,12,20,255))
def m_act1(lay,d):
    rng=random.Random(3)
    for k in range(6):
        x0,y0=rng.randrange(80,820),rng.randrange(80,820)
        d.rectangle([x0,y0,x0+150,y0+200],outline=(70,62,50,120),width=2)
        for j in range(10): d.line([x0+14,y0+20+j*17,x0+136-rng.randrange(0,50),y0+20+j*17],fill=(90,80,62,90),width=2)
        d.ellipse([x0+55,y0+150,x0+95,y0+190],outline=(140,90,200,110),width=2)
def m_act2(lay,d):
    for i in range(30):
        x=i*36+random.randint(-6,6)
        d.line([x,0,x+random.randint(-30,30),S],fill=(200,170,80,40),width=random.randint(3,9))
    g=glow_layer((S,S),lambda dd:dd.polygon([(512,180),(560,500),(512,860),(470,500)],fill=255),40,(180,90,255)); lay.alpha_composite(g)
    d.polygon([(512,260),(536,500),(512,780),(490,500)],fill=(10,6,16,230))
def m_stars(lay,d):
    rng=random.Random(9)
    for c in range(9):
        cx,cy=rng.randrange(60,960),rng.randrange(60,960)
        pts=[(cx+rng.randrange(-120,120),cy+rng.randrange(-120,120)) for _ in range(rng.randrange(4,7))]
        d.line(pts,fill=(120,160,230,90),width=2)
        for p in pts: d.ellipse([p[0]-4,p[1]-4,p[0]+4,p[1]+4],fill=(200,220,255,200))
    g=glow_layer((S,S),lambda dd:chaos_star(dd,820,200,90,32),25,(170,90,255)); lay.alpha_composite(g)
    chaos_star(d,820,200,70,25,fill=(200,170,255,150),width=2)
def m_act3(lay,d):
    rng=random.Random(4)
    for k in range(40):
        x,y=512,700
        pts=[(x,y)]
        a=rng.uniform(0,2*math.pi)
        for s in range(12):
            a+=rng.uniform(-0.5,0.5); x+=math.cos(a)*35; y+=math.sin(a)*35; pts.append((x,y))
        d.line(pts,fill=(110,40,150,90),width=rng.randrange(2,6))
    g=glow_layer((S,S),lambda dd:dd.ellipse([470,640,554,760],fill=255),30,(170,60,220)); lay.alpha_composite(g)
    d.polygon([(500,720),(524,720),(516,520),(508,520)],fill=(8,6,12,255))
def m_tech(lay,d):
    rng=random.Random(6)
    for k in range(7):
        cx,cy,r=rng.randrange(100,920),rng.randrange(100,920),rng.randrange(50,120)
        teeth=12
        pts=[]
        for t in range(teeth*2):
            rr=r if t%2==0 else r*0.82
            a=t*math.pi/teeth
            pts.append((cx+rr*math.cos(a),cy+rr*math.sin(a)))
        d.polygon(pts,outline=(110,115,130,100))
        d.ellipse([cx-r*0.35,cy-r*0.35,cx+r*0.35,cy+r*0.35],outline=(150,100,220,110),width=2)
    for k in range(25):
        x,y=rng.randrange(0,S),rng.randrange(0,S)
        d.line([x,y,x+rng.choice([-1,1])*rng.randrange(60,200),y],fill=(90,140,200,70),width=2)
chapter('act0',10,[80,40,140],[30,25,70],m_act0)
chapter('act1',20,[70,55,40],[40,30,60],m_act1)
chapter('act2',30,[110,90,40],[70,30,120],m_act2)
chapter('stars',40,[40,60,140],[70,40,130],m_stars)
chapter('act3',50,[90,30,110],[50,10,40],m_act3)
chapter('tech',60,[40,70,90],[70,40,110],m_tech)
print('ok')
