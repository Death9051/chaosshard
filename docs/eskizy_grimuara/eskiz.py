import random, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter
D='/tmp/claude-0/-home-claude/3e1861cf-f101-5e62-9da8-2898cd184384/scratchpad/'
FD=D+'fonts/'
shot=Image.open(D+'ct/ct_06_grimoire_ritual_page.png').convert('RGBA')
def icon(x,y):
    ic=shot.crop((x,y,x+32,y+32)); bg=ic.getpixel((0,0)); px=ic.load()
    for i in range(32):
        for j in range(32):
            p=px[i,j]
            if sum(abs(p[k]-bg[k]) for k in range(3))<40: px[i,j]=(0,0,0,0)
    return ic
L_ICONS=[icon(391,199+44*k) for k in range(11)]
BONE=icon(698,319); ESS=icon(698,359)
TAB_ICONS=[icon(392,134),icon(608,134),icon(824,134)]
B=ImageFont.truetype(FD+'alegreya-400.ttf',20); BB=ImageFont.truetype(FD+'alegreya-700.ttf',27)
BC=ImageFont.truetype(FD+'alegreya-700.ttf',34); S=ImageFont.truetype(FD+'alegreya-400.ttf',17)
HW=ImageFont.truetype(FD+'bad-script-400.ttf',20); HWs=ImageFont.truetype(FD+'bad-script-400.ttf',17)

def noise(img,box,amt,seed):
    r=random.Random(seed); px=img.load(); x0,y0,x1,y1=box
    for y in range(y0,y1):
        for x in range(x0,x1):
            p=px[x,y]; d=r.randint(-amt,amt)
            px[x,y]=(max(0,min(255,p[0]+d)),max(0,min(255,p[1]+d)),max(0,min(255,p[2]+d)),p[3])

def rot_text(img,xy,s,font,fill,angle):
    w=int(font.getlength(s))+8; h=font.size*2
    t=Image.new('RGBA',(w,h),(0,0,0,0)); ImageDraw.Draw(t).text((4,2),s,font=font,fill=fill)
    t=t.rotate(angle,expand=True,resample=Image.BICUBIC); img.alpha_composite(t,xy)

def title(d,x,y,s,ink,red):
    asc=BC.getmetrics()[0]-BB.getmetrics()[0]
    d.text((x,y),s[0],font=BC,fill=red); w=d.textlength(s[0],font=BC)
    d.text((x+w+1,y+asc),s[1:],font=BB,fill=ink)

def book(variant):
    random.seed(3)
    img=Image.open(D+'ct/ct_03.png').convert('RGBA').filter(ImageFilter.GaussianBlur(3))
    img.alpha_composite(Image.new('RGBA',img.size,(0,0,0,160)))
    W,H=440,256
    g=Image.new('RGBA',(W+40,H+40),(0,0,0,0)); d=ImageDraw.Draw(g); ox,oy=20,26
    d.rectangle((ox+3,oy+4,ox+W+3,oy+H+4),fill=(0,0,0,110))
    leather=(46,22,52,255) if variant=='A' else (24,14,30,255)
    d.rectangle((ox,oy,ox+W,oy+H),fill=leather); noise(g,(ox,oy,ox+W,oy+H),7,1)
    for x in range(ox+5,ox+W-5,4): g.putpixel((x,oy+3),(150,110,70,255)); g.putpixel((x,oy+H-3),(150,110,70,255))
    for y in range(oy+5,oy+H-5,4): g.putpixel((ox+3,y),(150,110,70,255)); g.putpixel((ox+W-3,y),(150,110,70,255))
    pl,pt,pr,pb=ox+10,oy+9,ox+W-10,oy+H-9; mid=(pl+pr)//2
    paper,edge=((226,210,176),(176,150,108)) if variant=='A' else ((34,24,46),(16,10,22))
    for k in range(3): d.rectangle((pl-1+k,pt+1+k,pr+1-k,pb+2-k),fill=(edge[0]-10*k,edge[1]-10*k,edge[2]-10*k,255))
    d.rectangle((pl,pt,pr,pb),fill=paper+(255,)); noise(g,(pl,pt,pr,pb),6 if variant=='A' else 4,2)
    px=g.load()
    for y in range(pt,pb):
        for x in range(pl,pr):
            dx=min(x-pl,pr-x); dy=min(y-pt,pb-y); dg=abs(x-mid); s=0
            if dx<10: s+=(10-dx)*3
            if dy<8: s+=(8-dy)*3
            if dg<14: s+=(14-dg)*4
            if variant=='A' and random.random()<0.004: s+=25
            p=px[x,y]; px[x,y]=(max(0,p[0]-s),max(0,p[1]-int(s*1.05)),max(0,p[2]-int(s*1.2)),255)
    d.line((mid,pt,mid,pb),fill=(edge[0]-30,edge[1]-30,edge[2]-30,255) if variant=='A' else (6,2,10,255))
    gold=(212,172,96,255); gd=(120,84,40,255)
    for cx,cy,sx,sy in ((ox,oy,1,1),(ox+W,oy,-1,1),(ox,oy+H,1,-1),(ox+W,oy+H,-1,-1)):
        d.polygon([(cx,cy),(cx+16*sx,cy),(cx,cy+16*sy)],fill=gd); d.polygon([(cx+sx,cy+sy),(cx+13*sx,cy+sy),(cx+sx,cy+13*sy)],fill=gold)
        d.ellipse((cx+4*sx-2,cy+4*sy-2,cx+4*sx+2,cy+4*sy+2),fill=(150,80,220,255))
    rcx,rcy=(mid+pr)//2+42,(pt+pb)//2+28; wm=(208,190,152,255) if variant=='A' else (50,35,68,255)
    for r in (44,38): d.ellipse((rcx-r,rcy-r,rcx+r,rcy+r),outline=wm)
    for a in range(8):
        t=a*math.pi/4
        d.line((rcx+44*math.cos(t),rcy+44*math.sin(t),rcx+44*math.cos(t+math.pi*3/4),rcy+44*math.sin(t+math.pi*3/4)),fill=wm)
    for i,col in enumerate(((120,40,150),(150,40,46),(40,110,100))):
        rx=ox+30+i*34; top=oy-20 if i==1 else oy-13
        d.rectangle((rx,top,rx+26,oy+4),fill=col+(255,))
        d.rectangle((rx,top,rx+2,oy+4),fill=tuple(min(255,c+40) for c in col)+(255,))
        d.rectangle((rx+24,top,rx+26,oy+4),fill=tuple(max(0,c-40) for c in col)+(255,))
    d.polygon([(mid+70,pb),(mid+78,pb),(mid+78,oy+H+14),(mid+74,oy+H+10),(mid+70,oy+H+14)],fill=(150,40,46,255))
    G=g.resize((g.width*2,g.height*2),Image.NEAREST); bx,by=(1600-G.width)//2,(900-G.height)//2+10
    img.alpha_composite(G,(bx,by)); d=ImageDraw.Draw(img)
    def P(x,y): return (bx+x*2,by+y*2)
    for i in range(3):
        rx=ox+30+i*34; top=oy-20 if i==1 else oy-13
        img.alpha_composite(TAB_ICONS[i],(P(rx,top)[0]+10,P(rx,top)[1]+5))
    if variant=='A':
        ink=(52,34,22); soft=(120,94,66); red=(150,30,30); vio=(96,40,130); gold2=(140,96,30); good=(40,110,40); bad=(160,40,30)
        note=(70,52,140); note_soft=(70,52,140,170)
    else:
        ink=(218,204,236); soft=(150,130,178); red=(236,176,100); vio=(200,146,255); gold2=(236,190,110); good=(140,220,130); bad=(240,120,110)
        note=(214,180,110); note_soft=(214,180,110,170)
    # ---------- left page
    lx,ly=P(pl+22,pt+9)
    title(d,lx,ly,'Ритуалы Чаши',ink,red)
    fy=ly+46; d.line((lx,fy,lx+330,fy),fill=soft,width=1); d.polygon([(lx+165,fy-4),(lx+170,fy),(lx+165,fy+4),(lx+160,fy)],fill=red)
    names=['Круг Хаоса','Рост','Смена неба','Оберег','Буря Хаоса','Зов тварей','Порыв Скверны','Сумерки','Приплод']
    yy=fy+12
    for k,n in enumerate(names):
        sel=k==1
        img.alpha_composite(L_ICONS[k],(lx,yy-3))
        d.text((lx+40,yy+1),n,font=B,fill=red if sel else (gold2 if k==0 else ink))
        w=int(d.textlength(n,font=B)); num=str(3+2*k)
        for x in range(lx+46+w,lx+312,7): d.point((x,yy+19),fill=soft)
        d.text((lx+330-d.textlength(num,font=S),yy+3),num,font=S,fill=soft)
        if sel: d.polygon([(lx-30,yy+2),(lx-16,yy+2),(lx-16,yy+26),(lx-23,yy+20),(lx-30,yy+26)],fill=red)
        yy+=38
    # marginalia left: ticks and interlinear notes
    def row(k): return fy+12+38*k
    for k in (0,1,2):
        cx,cy=lx+286,row(k)+12
        d.line((cx,cy,cx+4,cy+5),fill=note,width=2); d.line((cx+4,cy+5,cx+12,cy-6),fill=note,width=2)
    d.text((lx+128,row(4)+19),'‸ не в доме!',font=HWs,fill=note)
    d.text((lx+118,row(7)+19),'‸ уголь — любой',font=HWs,fill=note)
    # footer
    d.text((lx,P(0,pb-16)[1]),'Нить: 3',font=S,fill=soft); d.text((lx+300,P(0,pb-16)[1]),'— 4 —',font=S,fill=soft)
    # ---------- right page
    rx,ry=P(mid+16,pt+9)
    title(d,rx,ry,'Рост',ink,red)
    d.text((rx+356-d.textlength('Нить 0',font=S),ry+12),'Нить 0',font=S,fill=soft)
    fy=ry+46; d.line((rx,fy,rx+356,fy),fill=soft,width=1); d.polygon([(rx+178,fy-4),(rx+183,fy),(rx+178,fy+4),(rx+173,fy)],fill=red)
    lines=['Десять минут растения в восьми','блоках вокруг сферы растут сами —','будто от костной муки.']
    yy=fy+20
    for i,l in enumerate(lines):
        d.text((rx,yy),l,font=B,fill=ink)
        if i==0:
            # interlinear gloss above "восьми"
            xw=rx+d.textlength('Десять минут растения в ',font=B)
            d.text((xw+4,yy-16),'проверял — ровно 8',font=HWs,fill=note)
            d.line((xw,yy+23,xw+d.textlength('восьми',font=B),yy+23),fill=note,width=1)
        yy+=30
    yy+=8
    d.text((rx,yy),'Подношения в Чашу',font=B,fill=vio); yy+=32
    for ic,n,h,ok in ((BONE,'8 × Костная мука','есть 5',False),(ESS,'1 × Эссенция Хаоса','есть 3',True)):
        img.alpha_composite(ic,(rx,yy-4)); d.text((rx+40,yy),n,font=B,fill=ink)
        d.text((rx+356-d.textlength(h,font=B),yy),h,font=B,fill=good if ok else bad)
        d.line((rx+40,yy+28,rx+356,yy+28),fill=soft); yy+=40
    # note near offerings heading
    rot_text(img,(rx+196,fy+112),'мука — из костей ↘',HWs,note,3)
    # duration with correction
    yy+=2; t1='Длится '; d.text((rx,yy),t1,font=S,fill=soft); x1=rx+d.textlength(t1,font=S)
    d.text((x1,yy),'5 мин',font=S,fill=soft); wq=d.textlength('5 мин',font=S); d.line((x1-1,yy+11,x1+wq+1,yy+9),fill=note,width=2)
    d.text((x1+2,yy-17),'10!',font=HW,fill=note)
    d.text((x1+wq+8,yy),'· запечатывается в сферу',font=S,fill=soft)
    # bottom note slanted
    rot_text(img,(rx+40,P(0,pb-74)[1]),'Мел не жалеть — круг должен быть цел.',HW,note,3)
    # wax seal button
    sx,sy=rx+4,P(0,pb-38)[1]
    seal=(150,30,40) if variant=='A' else (110,50,170)
    d.ellipse((sx,sy,sx+50,sy+50),fill=seal); d.ellipse((sx+6,sy+6,sx+44,sy+44),outline=tuple(min(255,c+60) for c in seal),width=3)
    d.polygon([(sx+25,sy+12),(sx+31,sy+25),(sx+25,sy+38),(sx+19,sy+25)],fill=tuple(min(255,c+90) for c in seal))
    d.text((sx+62,sy+6),'Сложить в Чашу',font=B,fill=red if variant=='A' else gold2)
    d.text((sx+62,sy+29),'кладёт недостающее из сумки',font=S,fill=soft)
    # ink blot
    bxp,byp=rx+340,P(0,pb-24)[1]
    for k in range(7):
        rr=random.randint(2,5); a=random.random()*6.28; dd=random.randint(0,8)
        d.ellipse((bxp+dd*math.cos(a)-rr,byp+dd*math.sin(a)-rr,bxp+dd*math.cos(a)+rr,byp+dd*math.sin(a)+rr),fill=note)
    return img.convert('RGB')
book('A').save(D+'mock/grimoire2_A.png'); book('B').save(D+'mock/grimoire2_B.png')
