// Comprobaciones reproducibles del contenido renderizado; no sustituyen una auditoría WCAG completa.
export async function inspectAccessibility(page){return page.evaluate(()=>{
 const cache=new Map(),canvas=document.createElement('canvas');canvas.width=canvas.height=1;const ctx=canvas.getContext('2d',{willReadFrequently:true});
 const color=value=>{if(cache.has(value))return cache.get(value);ctx.clearRect(0,0,1,1);ctx.fillStyle=value;ctx.fillRect(0,0,1,1);const c=Array.from(ctx.getImageData(0,0,1,1).data);c[3]/=255;cache.set(value,c);return c;};
 const blend=(fg,bg)=>[0,1,2].map(i=>fg[i]*fg[3]+bg[i]*(1-fg[3]));
 const lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((v,n,i)=>v+n*[.2126,.7152,.0722][i],0);
 const root=document.querySelector('dialog[open]')||document.body,failures=[];let checked=0;
 for(const el of root.querySelectorAll('*')){
  if(!el.getClientRects().length||el.closest('svg,option,[aria-hidden=true]')||el.classList.contains('sr-only')||el.matches(':disabled')||getComputedStyle(el).visibility!=='visible')continue;
  const direct=Array.from(el.childNodes).filter(n=>n.nodeType===3).map(n=>n.textContent).join('').trim(),text=direct||((el.matches('input:not([type=checkbox]):not([type=radio]),textarea')&&el.value)||'');if(!text)continue;
  const ancestors=[];for(let p=el;p;p=p.parentElement)ancestors.unshift(p);let bg=[255,255,255];for(const p of ancestors)bg=blend(color(getComputedStyle(p).backgroundColor),bg);
  const style=getComputedStyle(el),fg=blend(color(style.color),bg),a=lum(fg),b=lum(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05),minimum=parseFloat(style.fontSize)>=24||parseFloat(style.fontSize)>=18.66&&Number(style.fontWeight)>=700?3:4.5;checked++;
  if(ratio<minimum-.03)failures.push({selector:el.id?'#'+el.id:el.tagName.toLowerCase()+'.'+String(el.className).replaceAll(' ','.'),text:text.slice(0,65),ratio:+ratio.toFixed(2),minimum,color:style.color,bg:bg.map(Math.round)});
 }
 const dialog=document.querySelector('dialog[open]'),footer=dialog?.querySelector('.dialog-foot,.privacy-footer'),bounds=footer?.getBoundingClientRect();
 return {contrast:{checked,failures},dialogFooterVisible:!bounds||bounds.top>=0&&bounds.bottom<=innerHeight,documentOverflow:document.documentElement.scrollWidth>innerWidth,reducedMotion:matchMedia('(prefers-reduced-motion:reduce)').matches};
 });}