/* Shared cubic strokes and animation samples for SVG and Android AVDs. */
(function(root){
  'use strict';
  const duration=600,interval=20,strokeWidth=44;
  const variants=[
    {id:'ink',name:'Ink flow',number:'01',subtitle:'One wave leads. The other follows.',description:'One continuous stroke draws the upper wave, followed by the lower wave. The drawing itself becomes the finished icon.',labels:['Seed','Lead','Follow','Draw','Finish','Complete']},
    {id:'counterpoint',name:'Counterpoint',number:'02',subtitle:'Two ends. One continuous mark.',description:'Two continuous strokes draw from opposite ends and connect. Their rounded tips become the final ends of the mark.',labels:['Seeds','Approach','Cross','Connect','Finish','Complete']},
    {id:'gather',name:'Gather',number:'03',subtitle:'Two fragments turn and find their place.',description:'Two rounded fragments draw outward while turning toward each other. Their tips align as the two waves join, then the finished mark rests.',labels:['Fragments','Open','Turn','Join','Settled','Complete']}
  ];
  const clamp=x=>Math.max(0,Math.min(1,x));
  const smooth=x=>{x=clamp(x);return x*x*(3-2*x);};
  const routes=[
    [[83,298],[120,233,142,168,205,168],[269,168,281,304,350,304],[390,304,410,248,429,214]],
    [[83,298],[113,245,129,207,159,207],[223,207,215,344,305,344],[370,344,401,263,429,214]]
  ];
  const mix=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t);
  function split(c,t){
    const a=mix(c[0],c[1],t),b=mix(c[1],c[2],t),d=mix(c[2],c[3],t);
    const e=mix(a,b,t),f=mix(b,d,t),p=mix(e,f,t);
    return [[c[0],a,e,p],[p,f,d,c[3]]];
  }
  const curves=routes.map(route=>{
    let start=route[0].map(v=>v*4/3+512/3);
    return route.slice(1).map(values=>{const p=values.map(v=>v*4/3+512/3);const c=[start,p.slice(0,2),p.slice(2,4),p.slice(4,6)];start=c[3];return c;});
  });
  const tables=curves.map(route=>{
    let length=0,previous=route[0][0];const table=[{length:0,segment:0,t:0}];
    route.forEach((c,segment)=>{for(let j=1;j<=160;j++){
      const t=j/160,p=split(c,t)[0][3];length+=Math.hypot(p[0]-previous[0],p[1]-previous[1]);previous=p;table.push({length,segment,t});
    }});return table;
  });
  function parameter(table,f){
    const d=clamp(f)*table.at(-1).length;let i=1;
    while(i<table.length-1&&table[i].length<d)i++;
    const a=table[i-1],b=table[i],u=(d-a.length)/(b.length-a.length);
    return b.segment+(a.segment===b.segment?a.t+(b.t-a.t)*u:b.t*u);
  }
  function stroke(route,table,from,to){
    const lo=parameter(table,from),hi=parameter(table,to);let first;
    const segments=route.map((c,i)=>{
      const a=clamp(lo-i),b=clamp(hi-i);
      let part;
      if(b<=a){const p=split(c,a)[0][3];part=[p,p,p,p];}
      else {part=split(c,b)[0];part=split(part,a/b)[1];}
      if(i===0)first=part[0];
      return part;
    });
    // Degenerate segments before/after the visible interval stay at the true
    // visible endpoint. They do not draw connecting lines or extra end caps.
    const startSegment=Math.min(2,Math.floor(lo)),endSegment=Math.min(2,Math.floor(hi));
    const start=split(route[startSegment],lo-startSegment)[0][3];
    const end=split(route[endSegment],hi-endSegment)[0][3];
    for(let i=0;i<3;i++)if(i<startSegment)segments[i]=[start,start,start,start];else if(i>endSegment)segments[i]=[end,end,end,end];
    return [...start,...segments.flatMap(c=>c.slice(1).flat())];
  }
  function strokePath(values){let i=0;const pair=()=>`${values[i++].toFixed(3)},${values[i++].toFixed(3)}`;let d='M'+pair();while(i<values.length)d+=` C${pair()} ${pair()} ${pair()}`;return d;}
  const finalPaths=curves.map((route,i)=>strokePath(stroke(route,tables[i],0,1)));
  function pose(id,time){return {layers:curves.map((route,i)=>{
    let from=0,to=0,rotation=0,tx=0,ty=0,age=time;
    if(id==='ink'){age=time-i*75;to=smooth(age/440);}
    else if(id==='counterpoint'){age=time-i*15;const progress=smooth(age/470);if(i===0)to=progress;else {from=1-progress;to=1;}}
    else {
      age=time-i*20;const growth=smooth(age/440),settle=1-smooth(time/420),center=i===0?.39:.61;
      from=center*(1-growth);to=center+(1-center)*growth;
      rotation=(i===0?-13:13)*settle;tx=(i===0?-24:24)*settle;ty=(i===0?-42:42)*settle;
    }
    return {points:stroke(route,tables[i],from,to),rotation,tx,ty,alpha:smooth(age/40)};
  })};}
  const frames=(geometry,id='gather')=>Array.from({length:duration/interval+1},(_,i)=>pose(id,i*interval));
  function sample(frames,time){
    const f=clamp(time/duration)*(frames.length-1),a=Math.floor(f),b=Math.min(a+1,frames.length-1),u=f-a;
    const lerp=(x,y)=>x+(y-x)*u;
    return {layers:frames[a].layers.map((layer,i)=>({points:mix(layer.points,frames[b].layers[i].points,u),...Object.fromEntries(['rotation','tx','ty','alpha'].map(k=>[k,lerp(layer[k],frames[b].layers[i][k])]))}))};
  }
  const api={duration,interval,strokeWidth,variants,strokePath,finalPaths,frames,sample};
  if(typeof module!=='undefined')module.exports=api;else root.HarmonicMotion=api;
})(typeof window==='undefined'?globalThis:window);
