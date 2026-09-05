(() => {
  'use strict';
  const geometry = window.HarmonicIcon, motion = window.HarmonicMotion;
  const sequences=Object.fromEntries(motion.variants.map(v=>[v.id,motion.frames(geometry,v.id)]));
  let selected=motion.variants.find(v=>v.id==='gather'), keyframes=sequences[selected.id];
  const basePath = motion.finalPaths.join(' ');
  const $ = id => document.getElementById(id);
  const staticStroke = d => `<path class="wave" fill="none" stroke="${geometry.ink}" stroke-width="${motion.strokeWidth}" stroke-linecap="round" stroke-linejoin="round" d="${d}"/>`;
  const svg = (d, reference = false, guides = false) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="170.667 170.667 682.666 682.666" role="img" aria-label="Harmonic wave icon"><circle cx="512" cy="512" r="341.333" fill="${geometry.background}"/>${reference ? `<image href="${window.HarmonicSource}" width="1024" height="1024"/>` : `${staticStroke(d)}`}${guides ? '<g class="safe-guides" fill="none" stroke="#af693e" stroke-width="3" stroke-dasharray="9 9"><circle cx="512" cy="512" r="341.333"/><circle cx="512" cy="512" r="284.444"/><path d="M170.667 512H853.333M512 170.667V853.333" opacity=".45"/></g>' : ''}</svg>`;
  function animatedSvg(pose,guides=false) {
    const layers=pose.layers.map(layer=>`<g class="ribbon-group" transform="translate(${layer.tx} ${layer.ty}) rotate(${layer.rotation} 512 512)"><path class="ribbon" fill="none" stroke="${geometry.ink}" stroke-width="${motion.strokeWidth}" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="${layer.alpha}" d="${motion.strokePath(layer.points)}"/></g>`).join('');
    return svg(basePath,false,guides).replace(staticStroke(basePath),layers);
  }
  $('main-icon').innerHTML = animatedSvg(keyframes[0],true);
  document.querySelector('.brand-mark').innerHTML = svg(basePath);
  $('reference-art').innerHTML = svg(basePath, true);
  $('vector-art').innerHTML = svg(basePath);
  $('compare-base').innerHTML = svg(basePath);
  $('compare-top').innerHTML = svg(basePath, true);
  const mainGroups = [...document.querySelectorAll('#main-icon .ribbon-group')];
  const mainRibbons = [...document.querySelectorAll('#main-icon .ribbon')];
  let time = 0, speed = 1, playing = false, raf = 0, lastTick = 0, holdUntil = 0;
  function filmstrip() {
    $('filmstrip').innerHTML = [0,120,240,360,480,600].map((t,i) => `<button class="frame" data-time="${t}" aria-label="Inspect ${selected.labels[i].toLowerCase()} at ${t} milliseconds"><span class="frame-image">${animatedSvg(motion.sample(keyframes,t))}</span><span class="frame-text"><strong>${selected.labels[i]}</strong><span>${t} ms</span></span></button>`).join('');
  }
  $('variant-picker').innerHTML=motion.variants.map(v=>`<button class="variant" data-variant="${v.id}" aria-label="${v.name}" aria-pressed="${v===selected}"><span class="variant-thumb">${animatedSvg(motion.sample(sequences[v.id],220))}</span><span class="variant-copy"><span class="variant-name"><small>${v.number}</small>${v.name}</span><span class="variant-subtitle">${v.subtitle}</span></span></button>`).join('');
  function describe() {
    $('motion-name').textContent=selected.name;$('motion-description').textContent=selected.description;
    $('avd-download').href=`export/ic_harmonic_splash_${selected.id}.xml`;$('avd-download').textContent=`${selected.name} · Android AVD ↗`;
    document.querySelectorAll('[data-variant]').forEach(b=>b.setAttribute('aria-pressed',b.dataset.variant===selected.id));
    filmstrip();
  }
  function render() {
    const cutoff = Number($('cutoff').value);
    const dismissed = cutoff < motion.duration && time >= cutoff;
    const pose=motion.sample(keyframes,time);
    pose.layers.forEach((layer,i)=>{
      mainGroups[i].setAttribute('transform',`translate(${layer.tx} ${layer.ty}) rotate(${layer.rotation} 512 512)`);
      mainRibbons[i].setAttribute('d',motion.strokePath(layer.points));mainRibbons[i].setAttribute('stroke-opacity',layer.alpha);
    });
    $('main-icon').hidden = dismissed;
    $('app-ready').hidden = !dismissed;
    $('time').textContent = Math.round(time);
    $('scrubber').value = Math.round(time);
    $('scrubber').style.setProperty('--progress', `${time/motion.duration*100}%`);
    $('scrubber').setAttribute('aria-valuetext', `${Math.round(time)} milliseconds${dismissed?', app ready':''}`);
    $('stage-status').textContent = dismissed ? `Dismissed at ${cutoff} ms` : time===0 ? 'Ready to build' : time>=motion.duration ? 'Complete' : playing ? 'Playing' : 'Paused';
    $('stage-caption').textContent = dismissed ? 'The app appears immediately. No animation hold.' : `${selected.number} · ${selected.name} — two continuous curves, from start to finish`;
    $('stage-label').textContent = dismissed ? 'EARLY DISMISSAL SIMULATION' : 'SPLASH PREVIEW';
    document.querySelectorAll('.frame').forEach(b => b.classList.toggle('active', Math.abs(Number(b.dataset.time)-time)<.5));
  }
  function stop() {
    playing=false; cancelAnimationFrame(raf); $('play').textContent='Play'; holdUntil=0; render();
  }
  function tick(stamp) {
    if (!playing) return;
    if (holdUntil) {
      if (stamp < holdUntil) { lastTick=stamp; raf=requestAnimationFrame(tick); return; }
      holdUntil=0; time=0; lastTick=stamp;
    }
    time=Math.min(Number($('cutoff').value), time+(stamp-lastTick)*speed);
    lastTick=stamp; render();
    if (time>=Number($('cutoff').value)) {
      if ($('loop').checked) holdUntil=stamp+900;
      else {stop();return;}
    }
    raf=requestAnimationFrame(tick);
  }
  function start(restart=false) {
    if(restart || time>=Number($('cutoff').value)) time=0;
    cancelAnimationFrame(raf); holdUntil=0; playing=true; $('play').textContent='Pause';
    lastTick=performance.now(); render(); raf=requestAnimationFrame(tick);
  }
  $('play').addEventListener('click',()=>playing?stop():start());
  $('replay').addEventListener('click',()=>start(true));
  $('scrubber').addEventListener('input',e=>{const next=Number(e.target.value);stop();time=next;render();});
  $('filmstrip').addEventListener('click',e=>{const b=e.target.closest('.frame');if(b){stop();time=Number(b.dataset.time);render();}});
  $('variant-picker').addEventListener('click',e=>{
    const b=e.target.closest('[data-variant]');if(!b)return;
    stop();selected=motion.variants.find(v=>v.id===b.dataset.variant);keyframes=sequences[selected.id];describe();
    if($('keep-time').checked)render();
    else {time=0;render();if(!matchMedia('(prefers-reduced-motion: reduce)').matches)start(true);}
  });
  document.querySelectorAll('[data-speed]').forEach(b=>b.addEventListener('click',()=>{
    speed=Number(b.dataset.speed);
    document.querySelectorAll('[data-speed]').forEach(x=>{const on=x===b;x.classList.toggle('selected',on);x.setAttribute('aria-pressed',on);});
  }));
  document.querySelectorAll('[data-theme]').forEach(b=>b.addEventListener('click',()=>{
    $('stage').classList.toggle('dark',b.dataset.theme==='dark');
    document.querySelectorAll('[data-theme]').forEach(x=>{const on=x===b;x.classList.toggle('selected',on);x.setAttribute('aria-pressed',on);});
  }));
  $('guides').addEventListener('change',()=> $('stage').classList.toggle('guides-on',$('guides').checked));
  $('loop').addEventListener('change',()=>{if (!$('loop').checked && holdUntil) stop();});
  $('cutoff').addEventListener('change',()=>{stop();time=0;render();});
  function switchView(match) {
    stop(); $('motion-panel').hidden=match; $('match-panel').hidden=!match;
    for(const [id,on] of [['motion-tab',!match],['match-tab',match]]){$(id).classList.toggle('active',on);$(id).setAttribute('aria-pressed',on);}
  }
  $('motion-tab').addEventListener('click',()=>switchView(false));
  $('match-tab').addEventListener('click',()=>switchView(true));
  function compare() {
    const mode=$('compare-mode').value;
    $('compare-top').style.clipPath=mode==='wipe'?`inset(0 ${100-Number($('wipe').value)}% 0 0)`:'none';
    $('compare-top').style.opacity=mode==='overlay'?'.5':'1';
    $('difference').hidden=mode!=='difference';
    $('wipe-line').hidden=mode!=='wipe';
    $('wipe-line').style.left=`${$('wipe').value}%`;
    $('wipe').disabled=mode!=='wipe';
    $('wipe-value').textContent=`${$('wipe').value}%`;
    $('comparison-title').textContent=mode==='difference'?'Difference ×8':mode==='overlay'?'50% overlay':'Wipe comparison';
    $('comparison-detail').textContent=mode==='difference'?'Black = match · light = edge difference':mode==='overlay'?'Look for doubled edges':'PNG left · vector right';
  }
  $('wipe').addEventListener('input',compare);
  $('compare-mode').addEventListener('change',compare);
  async function difference() {
    const load=src=>new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=reject;im.src=src;});
    const fullSvg=`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">${staticStroke(basePath)}</svg>`;
    const url=URL.createObjectURL(new Blob([fullSvg],{type:'image/svg+xml'}));
    try {
      const images=await Promise.all([load(window.HarmonicSource),load(url)]);
      const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
      const ctx=canvas.getContext('2d',{willReadFrequently:true});
      const pixels=images.map(im=>{ctx.clearRect(0,0,1024,1024);ctx.drawImage(im,0,0);return ctx.getImageData(0,0,1024,1024).data;});
      const diff=ctx.createImageData(1024,1024);
      let error=0, intersection=0, union=0, max=0;
      for(let i=0;i<pixels[0].length;i+=4){
        const a=pixels[0][i+3],b=pixels[1][i+3],delta=Math.abs(a-b);
        error+=delta;intersection+=Math.min(a,b);union+=Math.max(a,b);max=Math.max(max,delta);
        diff.data[i]=diff.data[i+1]=diff.data[i+2]=Math.min(255,delta*8);diff.data[i+3]=255;
      }
      // Crop the same adaptive-icon area used by the comparison panels.
      ctx.putImageData(diff,0,0);
      const target=$('difference').getContext('2d');target.drawImage(canvas,170.667,170.667,682.666,682.666,0,0,1024,1024);
      window.fidelityMetrics={alphaIoU:intersection/union,meanAlphaError:error/(1024*1024*255),maxAlphaError:max/255};
    } finally {URL.revokeObjectURL(url);}
  }
  describe();compare();render();difference().catch(e=>console.error('Fidelity comparison could not render',e));
})();

