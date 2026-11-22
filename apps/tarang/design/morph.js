// ---- Player <-> mini player morph (follows the finger) ----
const EASE = t => 1 - Math.pow(1 - t, 3);
function scaleK(){ const m=($('#frame').style.transform||'').match(/scale\(([\d.]+)\)/); return m?parseFloat(m[1]):1; }
function rectIn(el){ const f=$('#frame').getBoundingClientRect(), r=el.getBoundingClientRect(), k=scaleK();
  return {x:(r.left-f.left)/k, y:(r.top-f.top)/k, w:r.width/k, h:r.height/k}; }
const lerp=(a,b,t)=>a+(b-a)*t;
function rgb(c){ const m=c.match(/[\d.]+/g)||[0,0,0]; return m.slice(0,3).map(Number); }
function mixc(a,b,t){ const A=rgb(a),B=rgb(b); return 'rgb('+A.map((v,i)=>Math.round(lerp(v,B[i],t))).join(',')+')'; }
const KEYS=['cover','title','artist','play','prev','next'];
const PANELS=['15','16','17','18','19','21','22','23','24'];
let busy=false, M=null;

function underRoute(){ let i=stack.length-2; while(i>0 && PANELS.includes(stack[i])) i--; let r=stack[i]; if(r==='03b'&&playing) r='03'; return r; }
function screenLayer(r, z){
  const v=D.screens[key(r)][dark?'dark':'light'], m=dark?'dark':'light';
  let d=''; if(v.chrome==='tab') d=D.docks[m+(playing?'-tab-':'-idle-')+v.tab]; else if(v.chrome==='tab-idle') d=D.docks[m+'-idle-'+v.tab]; else if(v.chrome==='inner'&&playing) d=D.docks[m+'-inner'];
  const el=document.createElement('div');
  el.style.cssText=v.vars+';position:absolute;inset:0;z-index:'+z+';background:'+v.bg+';color:var(--onSurface);overflow:hidden';
  el.innerHTML='<div class="scr" style="overflow:hidden">'+v.html+'</div><div class="ldock" style="position:absolute;left:0;right:0;bottom:0">'+d+'</div>';
  $('#frame').appendChild(el); return el;
}
// find the four flying pieces in a player root and in a mini player
function playerParts(root){
  const all=[...root.querySelectorAll('div')];
  const cover=all.find(e=>e.style.width==='340px');
  const title=all.find(e=>e.style.fontSize==='26px');
  const artist=title?title.nextElementSibling:null;
  const play=all.find(e=>e.style.width==='104px');
  const pn=all.filter(e=>e.style.width==='64px'&&e.style.height==='64px');
  return {cover,title,artist,play,prev:pn[0],next:pn[1],bg:root.querySelector('.scr > div')||root.firstElementChild};
}
function miniParts(mini){
  const all=[...mini.querySelectorAll('div')];
  return {cover:all.find(e=>e.style.width==='52px'), title:all.find(e=>e.style.fontSize==='15px'),
          artist:all.find(e=>e.style.fontSize==='13px'), play:all.find(e=>e.style.width==='48px'),
          prev:all.filter(e=>e.style.width==='40px')[0], next:all.filter(e=>e.style.width==='40px')[1]};
}
// Build a morph between a player layer (pl) and a screen layer that holds the mini player (ul)
function buildMorph(pl, ul){
  const mini=ul.querySelector('[data-mini]'); if(!mini) return null;
  const P=playerParts(pl), Q=miniParts(mini);
  const R={}; for(const k of KEYS) R[k]=[rectIn(P[k]), rectIn(Q[k])];
  const box=[{x:0,y:0,w:$('#frame').offsetWidth,h:$('#frame').offsetHeight}, rectIn(mini)];
  const bgA=getComputedStyle(P.bg).backgroundColor, bgB=getComputedStyle(mini).backgroundColor;
  const pA=getComputedStyle(P.play).backgroundColor, pB=getComputedStyle(Q.play).backgroundColor;
  const tA=getComputedStyle(P.title).color, tB=getComputedStyle(Q.title).color;
  const nA=getComputedStyle(P.prev).backgroundColor, nB=getComputedStyle(Q.prev).backgroundColor;
  const nfA=getComputedStyle(P.prev).color, nfB=getComputedStyle(Q.prev).color;
  const piA=getComputedStyle(P.play).color, piB=getComputedStyle(Q.play).color;
  const aA=getComputedStyle(P.artist).color, aB=getComputedStyle(Q.artist).color;
  const ov=document.createElement('div'); ov.style.cssText='position:absolute;inset:0;z-index:20;pointer-events:none';
  const bx=document.createElement('div'); bx.style.cssText='position:absolute;overflow:hidden'; ov.appendChild(bx);
  const fill=mini.querySelector('.pfill'); const fl=document.createElement('div');
  fl.style.cssText='position:absolute;left:0;top:0;bottom:0;background:'+(fill?getComputedStyle(fill).backgroundColor:'transparent'); bx.appendChild(fl);
  const fly={};
  for(const k of KEYS){
    const c=P[k].cloneNode(true); c.style.position='absolute'; c.style.margin='0'; c.style.transformOrigin='0 0';
    if(k==='title'||k==='artist'){ c.style.whiteSpace='nowrap'; c.style.width='auto'; }
    ov.appendChild(c); fly[k]=c;
  }
  const at=fly.artist.textContent, cut=at.indexOf(' · ');
  if(cut>0){ fly.artist.innerHTML=''; fly.artist.append(at.slice(0,cut)); const sp=document.createElement('span'); sp.className='alb'; sp.textContent=at.slice(cut); fly.artist.appendChild(sp); }
  fly.artist.style.opacity='1';
  $('#frame').appendChild(ov);
  const nav=ul.querySelector('.ldock > div > div:last-child');
  P.mini=mini; mini.style.visibility='hidden';
  for(const k of KEYS) P[k].style.visibility='hidden';
  const pw=fill?parseFloat(fill.style.width)||prog:prog;
  return {pl,ul,ov,bx,fl,fly,R,box,bgA,bgB,pA,pB,tA,tB,nA,nB,nfA,nfB,piA,piB,aA,aB,nav,mini,P,pw};
}
function setT(m, t){ // t: 0 = full player, 1 = mini player
  const {R,box,fly}=m; const T=t; t=Math.min(t/0.85,1); // pieces land at 85%, then hand over
  const L=(i)=>({x:lerp(R[i][0].x,R[i][1].x,t), y:lerp(R[i][0].y,R[i][1].y,t), w:lerp(R[i][0].w,R[i][1].w,t), h:lerp(R[i][0].h,R[i][1].h,t)});
  const b={x:lerp(box[0].x,box[1].x,t),y:lerp(box[0].y,box[1].y,t),w:lerp(box[0].w,box[1].w,t),h:lerp(box[0].h,box[1].h,t)};
  Object.assign(m.bx.style,{left:b.x+'px',top:b.y+'px',width:b.w+'px',height:b.h+'px',background:mixc(m.bgA,m.bgB,t),
    borderRadius:lerp(28*Math.min(t*4,1),24,t)+'px '+lerp(28*Math.min(t*4,1),24,t)+'px '+lerp(0,10,t)+'px '+lerp(0,10,t)+'px'});
  m.fl.style.width=m.pw+'%'; m.fl.style.opacity=String(Math.max(0,(t-0.5)/0.5));
  const c=L('cover'); Object.assign(fly.cover.style,{left:c.x+'px',top:c.y+'px',width:c.w+'px',height:c.h+'px',borderRadius:lerp(32,c.w/2,t)+'px'});
  for(const k of ['title','artist']){ const r=L(k), s=r.h/R[k][0].h; Object.assign(fly[k].style,{left:r.x+'px',top:r.y+'px',transform:'scale('+s+')'}); }
  const p=L('play'); Object.assign(fly.play.style,{left:p.x+'px',top:p.y+'px',width:p.w+'px',height:p.h+'px',borderRadius:lerp(28,p.h/2,t)+'px',background:mixc(m.pA,m.pB,t),color:mixc(m.piA,m.piB,t)});
  const ic=fly.play.querySelector('.ms'); if(ic) ic.style.fontSize=lerp(44,24,t)+'px';
  for(const k of ['prev','next']){ const r=L(k); Object.assign(fly[k].style,{left:r.x+'px',top:r.y+'px',width:r.w+'px',height:r.h+'px',borderRadius:(r.h/2)+'px',background:mixc(m.nA,m.nB,t),color:mixc(m.nfA,m.nfB,t)});
    const i2=fly[k].querySelector('.ms'); if(i2) i2.style.fontSize=lerp(32,20,t)+'px'; }
  // last stretch: hand over smoothly from the flying copies to the real mini player
  fly.title.style.color=mixc(m.tA,m.tB,t); fly.artist.style.color=mixc(m.aA,m.aB,t);
  const alb=fly.artist.querySelector('.alb'); if(alb) alb.style.opacity=String(Math.max(0,1-t*1.6));
  m.pl.style.opacity=Math.max(0,1-t*2.2);              // rest of the player fades early
  m.ul.style.opacity=lerp(0.35,1,Math.min(t*1.3,1));   // screen behind comes in
  m.ul.style.transform=T>=0.7?'none':'scale('+lerp(0.94,1,t/0.7)+')';
  if(m.nav && !m.nav.hasAttribute('data-mini')) m.nav.style.transform='translateY('+((1-t)*120)+'%)';
}
function animateT(m, from, to, ms, done){
  const t0=performance.now();
  function step(now){ const k=Math.min((now-t0)/ms,1); setT(m, lerp(from,to,EASE(k))); if(k<1) requestAnimationFrame(step); else done&&done(); }
  requestAnimationFrame(step);
}
function cleanup(m){ m.ov.remove(); m.ul.remove(); }

// collapse: full player -> mini (from a finished drag or the down arrow)
function startCollapse(){
  const r=underRoute(); const ul=screenLayer(r, 1);
  const pl=$('#scr'); pl.style.zIndex='2';
  const m=buildMorph(pl, ul); if(!m){ ul.remove(); return null; } return m;
}
function finishCollapse(m, fromT){
  busy=true;
  animateT(m, fromT, 1, 300*(1-fromT)+140, ()=>{
    stack.pop(); while(stack.length>1 && PANELS.includes(stack[stack.length-1])) stack.pop();
    $('#scr').style.opacity=''; $('#scr').style.zIndex=''; show(); cleanup(m); busy=false; });
}
function collapse(){ if(busy) return; const m=startCollapse(); if(!m){ go('back'); return; } finishCollapse(m, 0); }

// expand: mini -> full player
function expand(){
  if(busy) return; busy=true;
  const cur=stack[stack.length-1];
  const ul=screenLayer(cur, 1); $('#scr').style.visibility='hidden'; $('#dock').style.visibility='hidden';
  const v=D.screens[key('14')][dark?'dark':'light'];
  const pl=document.createElement('div'); pl.style.cssText=v.vars+';position:absolute;inset:0;z-index:2;background:'+v.bg;
  pl.innerHTML='<div class="scr" style="overflow:hidden">'+v.html+'</div>'; $('#frame').appendChild(pl);
  const m=buildMorph(pl, ul); if(!m){ pl.remove(); ul.remove(); $('#scr').style.visibility=''; $('#dock').style.visibility=''; busy=false; go('14'); return; }
  setT(m,1);
  animateT(m, 1, 0, 420, ()=>{ stack.push('14'); show(); $('#scr').style.visibility=''; $('#dock').style.visibility=''; pl.remove(); cleanup(m); busy=false; });
}

// drag on the full player
(function(){ let y0=null, M=null, t=0;
  const DIST=420; // px of drag for a full shrink
  document.addEventListener('pointerdown',e=>{ if(stack[stack.length-1]!=='14'||busy||!e.target.closest('#scr')) return; y0=e.clientY; t=0; M=null; });
  document.addEventListener('pointermove',e=>{ if(y0===null) return; const dy=(e.clientY-y0)/scaleK();
    if(!M && dy>8){ M=startCollapse(); }
    if(M){ t=Math.max(0,Math.min(1,dy/DIST)); setT(M,t); } });
  document.addEventListener('pointerup',()=>{ if(y0===null) return; y0=null; const m=M; M=null; if(!m) return;
    if(t>0.28){ finishCollapse(m,t); }
    else { busy=true; animateT(m,t,0,220,()=>{ for(const k of KEYS) m.P[k].style.visibility=''; m.pl.style.opacity=''; m.pl.style.zIndex=''; cleanup(m); busy=false; }); } });
})();
