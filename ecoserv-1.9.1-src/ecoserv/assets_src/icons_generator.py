# Procedural 16x16 pixel-art animated icons (vertical strips, 8 frames) for EcoServ.
import math, sys, os
from PIL import Image

OUT = sys.argv[1]
N = 8
S = 16

def rgba(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

class F:
    def __init__(s): s.im = Image.new('RGBA', (S, S), (0,0,0,0)); s.px = s.im.load()
    def p(s, x, y, c):
        x=int(round(x)); y=int(round(y))
        if 0<=x<S and 0<=y<S: s.px[x,y]=c
    def get(s,x,y): return s.px[x,y] if 0<=x<S and 0<=y<S else (0,0,0,0)
    def rect(s,x0,y0,x1,y1,c):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1): s.p(x,y,c)
    def ellipse(s, cx, cy, rx, ry, fill, edge=None, shade=None, light=None):
        for y in range(S):
            for x in range(S):
                dx=(x+0.5-cx)/max(rx,0.01); dy=(y+0.5-cy)/max(ry,0.01); d=dx*dx+dy*dy
                if d<=1.0:
                    c=fill
                    if shade and (dx*0.6+dy*0.8)>0.45: c=shade
                    if light and (dx*0.6+dy*0.8)<-0.55: c=light
                    s.p(x,y,c)
        if edge: s.outline(edge, only_fill=[fill]+([shade] if shade else [])+([light] if light else []))
    def outline(s, edge, only_fill=None):
        pts=[]
        for y in range(S):
            for x in range(S):
                if s.px[x,y][3]==0:
                    for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                        q=s.get(x+dx,y+dy)
                        if q[3]>0 and (only_fill is None or q in only_fill): pts.append((x,y)); break
        for x,y in pts: s.p(x,y,edge)
    def line(s,x0,y0,x1,y1,c):
        n=int(max(abs(x1-x0),abs(y1-y0)))+1
        for i in range(n+1):
            t=i/max(n,1); s.p(x0+(x1-x0)*t, y0+(y1-y0)*t, c)

def strip(name, frames):
    im = Image.new('RGBA', (S, S*len(frames)))
    for i,f in enumerate(frames): im.paste(f.im, (0, i*S))
    im.save(os.path.join(OUT, name + '.png'))

def sparkle(f, x, y, phase, col=rgba('fff7c2')):
    # 4-point twinkle, size by phase 0..1
    if phase<=0: return
    f.p(x,y,col)
    if phase>0.4:
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)): f.p(x+dx,y+dy,rgba('ffe26b',200))
    if phase>0.8:
        for dx,dy in ((2,0),(-2,0),(0,2),(0,-2)): f.p(x+dx,y+dy,rgba('ffd23f',120))

GOLD=rgba('f6c945'); GOLD_D=rgba('c9922a'); GOLD_L=rgba('fff1a8'); GOLD_E=rgba('6b4314')

# 1. Spinning coin (bank)
def coin():
    fr=[]
    for i in range(N):
        f=F(); a=i/N*2*math.pi; w=abs(math.cos(a))*5.5+0.8
        f.ellipse(8,8.5,w,6.0,GOLD,GOLD_E,GOLD_D,GOLD_L)
        if w>3.5:
            f.rect(8,6,8,10,GOLD_D); f.p(7,6,GOLD_D); f.p(9,10,GOLD_D)   # stamped bar
        else:
            for y in range(4,13): f.p(8 if math.cos(a)>0 else 7, y, GOLD_L)
        sparkle(f, 12, 3, [0,0.5,1,0.5,0,0,0,0][i])
        fr.append(f)
    strip('anim_bank', fr)

# 2. Ledger with writing lines (history)
def ledger():
    fr=[]
    for i in range(N):
        f=F()
        paper=rgba('f3ead2'); edge=rgba('5a4632'); line_c=rgba('8a7a62'); ink=rgba('2f5fb3')
        f.rect(3,2,12,13,paper)
        for y in range(2,14): f.p(3,y,rgba('d9cba7'))
        f.outline(edge)
        f.rect(2,2,2,13,rgba('8b2f2f'))  # spine
        rows=[4,6,8,10,12]
        prog=i/(N-1)*len(rows)
        for k,y in enumerate(rows):
            L=min(1.0,max(0.0,prog-k))
            xe=5+int(L*6)
            for x in range(5,xe+1): f.p(x,y,line_c)
            if 0<prog-k<1: f.p(xe,y,ink); f.p(xe+1,y-1,ink)  # pen tip
        if i==N-1: f.p(11,12,rgba('3fae49')); f.p(10,11,rgba('3fae49'))
        fr.append(f)
    strip('anim_history', fr)

# 3. Hidden reward: open eye blinks (revealed) / closed eye with padlock (rewards hidden)
def eye(name, open_):
    fr=[]
    for i in range(N):
        f=F()
        white=rgba('f4f1ff'); edge=rgba('3b2a59'); iris=rgba('3fb6ff'); pupil=rgba('1a1030')
        if open_:
            lid=[1,1,0.9,0.5,0.1,0.5,0.9,1][i]
            ry=4.2*lid
            if ry>0.6:
                f.ellipse(8,8,6.4,ry,white,edge)
                if ry>1.5:
                    f.ellipse(8,8,2.6,min(2.6,ry),iris)
                    f.p(8,8,pupil); f.p(7,8,pupil); f.p(9,7,rgba('ffffff'))
            else:
                f.line(2,8,14,8,edge)
            sparkle(f,13,3,[0,0,0.5,1,0.5,0,0,0][i],rgba('d6fbff'))
        else:
            # closed eye: curved lid + lashes
            lidc=rgba('d9ccff'); y0=6
            for x in range(2,15):
                y=y0+round(2.2*math.sin((x-2)/12*math.pi))
                f.p(x,y,edge); f.p(x,y-1,lidc)
            for x in (4,7,10,13):
                y=y0+round(2.2*math.sin((x-2)/12*math.pi))
                f.p(x-1,y+1,edge); f.p(x-1,y+2,edge)
            # padlock bottom right
            lock=rgba('f6c945'); le=rgba('6b4314'); sh=rgba('9aa3b5')
            bob=[0,0,1,1,0,0,0,0][i]
            f.p(11,10+bob,sh); f.p(14,10+bob,sh); f.p(12,9+bob,sh); f.p(13,9+bob,sh)
            f.rect(10,11+bob,15,15,lock); f.p(12,13+bob,le); f.p(12,14+bob,le); f.p(13,13+bob,le)
            # pulsing question mark
            t=[0,0.3,0.6,1,0.6,0.3,0,0][i]
            if t>0.2:
                q=rgba('c9a0ff') if t<0.9 else rgba('f0e2ff')
                for (x,y) in ((2,10),(3,9),(4,9),(5,10),(5,11),(4,12),(4,14)): f.p(x,y,q)
        fr.append(f)
    strip(name, fr)

# 4. Money bag bouncing
def bag():
    fr=[]
    for i in range(N):
        f=F(); off=[0,-1,-2,-2,-1,0,0,0][i]; sq=[0,0,0,0,0,0,1,0][i]
        cloth=rgba('b98a4f'); dark=rgba('8a6236'); light=rgba('dcb57d'); edge=rgba('4a2f14'); tie=rgba('d9433d')
        f.ellipse(8,10.5+off+sq*0.5,5.6+sq*0.6,4.6-sq*0.5,cloth,None,dark,light)
        f.rect(6,4+off,10,6+off,cloth); f.p(5,4+off,cloth); f.p(11,4+off,cloth)
        f.outline(edge)
        f.rect(6,6+off,10,6+off,tie)
        sym=rgba('f6c945')
        for (x,y) in ((8,7),(7,8),(8,8),(9,8),(7,9),(8,10),(9,11),(7,12),(8,12),(9,12),(8,13)): f.p(x,y+off,sym)
        sparkle(f,13,4+off,[0,0,0.4,1,0.4,0,0,0][i])
        sparkle(f,3,6+off,[0,0,0,0,0.5,1,0.5,0][i])
        fr.append(f)
    strip('anim_money', fr)

# 5. Role badge (star medal with rotating glint)
def badge():
    fr=[]
    for i in range(N):
        f=F()
        rib1=rgba('3d6fd6'); rib2=rgba('d84a4a')
        for y in range(0,6):
            f.p(5+y//3,y,rib1); f.p(6+y//3,y,rib1); f.p(10-y//3,y,rib2); f.p(9-y//3,y,rib2)
        f.ellipse(8,10,5,5,GOLD,GOLD_E,GOLD_D,GOLD_L)
        star=rgba('fffbe0')
        pts=[]
        for k in range(10):
            r=3.8 if k%2==0 else 1.6; ang=-math.pi/2+k*math.pi/5
            pts.append((8+math.cos(ang)*r,10+math.sin(ang)*r))
        for y in range(16):
            for x in range(16):
                px,py=x+0.5,y+0.5; inside=False; j=len(pts)-1
                for k in range(len(pts)):
                    xi,yi=pts[k]; xj,yj=pts[j]
                    if (yi>py)!=(yj>py) and px<(xj-xi)*(py-yi)/(yj-yi+1e-9)+xi: inside=not inside
                    j=k
                if inside: f.p(x,y,star)
        g=i-2
        if 0<=g<5:
            for d in range(-1,2): f.p(4+g*2+d, 13-g*2+d, rgba('ffffff',200))
        fr.append(f)
    strip('anim_role', fr)

# 6. Portal swirl (subworld access)
def portal():
    fr=[]
    for i in range(N):
        f=F()
        f.ellipse(8,8,6.5,7.2,rgba('2a0f4a'),rgba('120624'))
        for k in range(3):
            for t in range(28):
                r=t/28*6.0; ang=t*0.32+i/N*2*math.pi+k*2*math.pi/3
                c=rgba('b26bff') if t%4 else rgba('f0d6ff')
                f.p(8+math.cos(ang)*r, 8+math.sin(ang)*r*1.1, c)
        f.p(8,8,rgba('ffffff')); f.p(7,8,rgba('e2c6ff'))
        fr.append(f)
    strip('anim_portal', fr)

# 7. Border expansion: frame grows, four arrows push outward
def expand():
    fr=[]
    for i in range(N):
        f=F(); e=[0,0.5,1,1.5,2,2.5,3,0][i]
        frame=rgba('aab3c5'); bar=rgba('6c7487'); arrow=rgba('59d36b'); ae=rgba('1f6b2b')
        lo=int(4-e//1); hi=int(11+e//1)
        lo=max(0,min(4,int(round(4-e)))); hi=min(15,max(11,int(round(11+e))))
        for x in range(lo,hi+1):
            f.p(x,lo,frame if x%2==0 else bar); f.p(x,hi,frame if x%2==0 else bar)
        for y in range(lo,hi+1):
            f.p(lo,y,frame if y%2==0 else bar); f.p(hi,y,frame if y%2==0 else bar)
        d=int(round(1+e*0.7))
        c0,c1=7,8
        # up
        for (x,y) in ((c0,c0-d),(c1,c0-d),(c0-1,c0-d+1),(c1+1,c0-d+1),(c0,c0-d+1),(c1,c0-d+1)): f.p(x,y,arrow)
        # down
        for (x,y) in ((c0,c1+d),(c1,c1+d),(c0-1,c1+d-1),(c1+1,c1+d-1),(c0,c1+d-1),(c1,c1+d-1)): f.p(x,y,arrow)
        # left
        for (x,y) in ((c0-d,c0),(c0-d,c1),(c0-d+1,c0-1),(c0-d+1,c1+1),(c0-d+1,c0),(c0-d+1,c1)): f.p(x,y,arrow)
        # right
        for (x,y) in ((c1+d,c0),(c1+d,c1),(c1+d-1,c0-1),(c1+d-1,c1+1),(c1+d-1,c0),(c1+d-1,c1)): f.p(x,y,arrow)
        f.rect(7,7,8,8,rgba('e9fff0'))
        fr.append(f)
    strip('anim_expand', fr)

# 8. Auction gavel: rotates around the handle end and strikes the block
def hammer():
    fr=[]
    degs=[72,64,50,34,26,36,52,66]
    for i in range(N):
        f=F(); th=math.radians(degs[i])
        block=rgba('7a4b2a'); blk_l=rgba('a06a3e'); edge=rgba('2e1c0f')
        f.rect(1,13,9,15,block); f.rect(2,13,8,13,blk_l)
        px,py=13.0,13.0
        dx,dy=-math.cos(th),-math.sin(th)
        for t in range(0,8):
            f.p(px+dx*t, py+dy*t, rgba('c08a5a'))
        hx,hy=px+dx*8.0, py+dy*8.0
        nx,ny=dy,-dx
        for u in range(-3,4):
            for v in (-1,0,1):
                c=rgba('8a5530') if abs(u)<3 else rgba('d8b36a')
                f.p(hx+nx*u*0.75+dx*v*0.9, hy+ny*u*0.75+dy*v*0.9, c)
        f.outline(edge)
        if i==4: sparkle(f,3,11,1); sparkle(f,9,10,0.5)
        if i==3: sparkle(f,4,11,0.4)
        fr.append(f)
    strip('anim_auction', fr)

# 9. Gear (server)
def gear():
    fr=[]
    for i in range(N):
        f=F(); rot=i/N*(2*math.pi/8)
        body=rgba('9aa3b5'); dark=rgba('5c6475'); edge=rgba('262a33')
        for y in range(16):
            for x in range(16):
                dx=x+0.5-8; dy=y+0.5-8; r=math.hypot(dx,dy); a=math.atan2(dy,dx)-rot
                tooth=(math.cos(a*8)>0.3)
                if r<=4.6 or (tooth and r<=6.6):
                    f.p(x,y, dark if (dx+dy)>2 else body)
                if r<=1.8: f.p(x,y,(0,0,0,0))
        f.outline(edge)
        fr.append(f)
    strip('anim_server', fr)

# 10. Price tag swinging
def tag():
    fr=[]
    for i in range(N):
        f=F(); sw=[0,1,1.5,1,0,-1,-1.5,-1][i]*0.09
        card=rgba('ffd76b'); edge=rgba('6b4a14'); hole=rgba('2a2a2a')
        cx,cy=8,3
        pts=[(-3,2),(3,2),(5,6),(5,12),(-5,12),(-5,6)]
        poly=[(cx+x*math.cos(sw)-y*math.sin(sw), cy+x*math.sin(sw)+y*math.cos(sw)) for x,y in pts]
        for y in range(16):
            for x in range(16):
                px,py=x+0.5,y+0.5; inside=False; j=len(poly)-1
                for k in range(len(poly)):
                    xi,yi=poly[k]; xj,yj=poly[j]
                    if (yi>py)!=(yj>py) and px<(xj-xi)*(py-yi)/(yj-yi+1e-9)+xi: inside=not inside
                    j=k
                if inside: f.p(x,y,card)
        f.outline(edge)
        f.line(8,0,cx-math.sin(sw)*4, cy+math.cos(sw)*4-1, rgba('c0c0c0'))
        hx,hy=cx-math.sin(sw)*4, cy+math.cos(sw)*4
        f.p(hx,hy,hole)
        tx,ty=cx-math.sin(sw)*9, cy+math.cos(sw)*9
        for (x,y) in ((0,-2),(0,-1),(0,0),(0,1),(0,2),(-1,-1),(1,1),(-1,1),(1,-1)):
            if (x,y) in ((-1,-1),(1,1)): continue
            f.p(tx+x,ty+y,rgba('2f7d32'))
        f.p(tx-1,ty-1,rgba('2f7d32')); f.p(tx+1,ty+1,rgba('2f7d32'))
        fr.append(f)
    strip('anim_prices', fr)

# 11. Profile (player bust with name card shimmer)
def profile():
    fr=[]
    for i in range(N):
        f=F()
        skin=rgba('e9b98f'); hair=rgba('5b3a21'); shirt=rgba('3c8fd6'); edge=rgba('22252b')
        f.rect(5,3,10,8,skin); f.rect(5,2,10,3,hair); f.p(5,4,hair); f.p(10,4,hair)
        f.p(6,5,edge); f.p(9,5,edge); f.p(7,7,rgba('c98a6a')); f.p(8,7,rgba('c98a6a'))
        f.rect(3,10,12,14,shirt); f.rect(4,9,11,9,shirt)
        f.outline(edge)
        sx=2+i*2
        for y in range(9,15):
            x=sx+(y-9)//2
            if f.get(x,y)==shirt: f.p(x,y,rgba('8fd0ff'))
        fr.append(f)
    strip('anim_profile', fr)

# 12. Players (two heads, chat bubble pulsing)
def players():
    fr=[]
    for i in range(N):
        f=F()
        def head(x0,y0,skin,hair):
            f.rect(x0,y0,x0+4,y0+4,skin); f.rect(x0,y0,x0+4,y0,hair)
            f.p(x0+1,y0+2,rgba('22252b')); f.p(x0+3,y0+2,rgba('22252b'))
            f.rect(x0-1,y0+6,x0+5,y0+8,rgba('5aa65e') if hair==rgba('2b2b2b') else rgba('d6833c'))
        head(2,6,rgba('e9b98f'),rgba('2b2b2b'))
        head(9,7,rgba('c98f6a'),rgba('f1d36b'))
        f.outline(rgba('22252b'))
        t=[0,0.3,0.7,1,1,0.7,0.3,0][i]
        if t>0.2:
            f.rect(9,1,14,4,rgba('ffffff')); f.p(10,5,rgba('ffffff'))
            for x in range(10,14,2): f.p(x,2+(1 if t<0.8 else 0),rgba('3fa34d'))
        fr.append(f)
    strip('anim_players', fr)

# 13. Hourglass (portal cooldown) — sand flowing
def hourglass():
    fr=[]
    for i in range(N):
        f=F(); glass=rgba('cfe9ff',150); wood=rgba('8a5a2b'); sand=rgba('f2c56b'); edge=rgba('3b2614')
        f.rect(3,1,12,2,wood); f.rect(3,13,12,14,wood)
        for y in range(3,13):
            w=abs(y-7.5)/4.5*4+0.6
            for x in range(16):
                if abs(x+0.5-8)<=w: f.p(x,y,glass)
        top=1-(i/(N-1)); 
        for y in range(3,8):
            w=abs(y-7.5)/4.5*4+0.6
            if y>=3+int((1-top)*5):
                for x in range(16):
                    if abs(x+0.5-8)<=w-0.3: f.p(x,y,sand)
        for y in range(8,13):
            w=abs(y-7.5)/4.5*4+0.6
            if y>=12-int((1-top)*4.5):
                for x in range(16):
                    if abs(x+0.5-8)<=w-0.3: f.p(x,y,sand)
        if i<N-1: f.line(8,8,8,11,sand)
        f.outline(edge)
        fr.append(f)
    strip('anim_hourglass', fr)

for fn in (coin, ledger, lambda: eye('anim_hidden', False), lambda: eye('anim_hidden_open', True), bag, badge, portal, expand, hammer, gear, tag, profile, players, hourglass):
    fn()
print('ok')

# 14. Trade arrows sliding (inventory tab)
def trade():
    fr=[]
    for i in range(N):
        f=F(); o=[0,1,2,3,3,2,1,0][i]
        blue=rgba('2f7fd6'); blue_d=rgba('1f4f8a'); green=rgba('3fbf5a'); green_d=rgba('237a37')
        y1=5; y2=10
        for x in range(2+o-1, 12+o-1): f.p(x,y1,blue)
        for d in range(0,3):
            f.p(12+o-1-d, y1-d, blue); f.p(12+o-1-d, y1+d, blue)
        for x in range(4-o+1, 14-o+1): f.p(x,y2,green)
        for d in range(0,3):
            f.p(4-o+1+d, y2-d, green); f.p(4-o+1+d, y2+d, green)
        fr.append(f)
    strip('anim_trade', fr)
trade()
