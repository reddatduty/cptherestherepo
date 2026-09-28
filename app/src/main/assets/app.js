(() => {
'use strict';
const TILE=256, MIN_Z=3, MAX_Z=18;
const categories={
  crime:{label:'Crime / theft',emoji:'⚠',accent:'#ff4d68'},
  suspicious:{label:'Suspicious',emoji:'◉',accent:'#ffb020'},
  safety:{label:'Safety issue',emoji:'!',accent:'#365cff'},
  paranormal:{label:'Paranormal',emoji:'✦',accent:'#8b5cf6'},
  other:{label:'Other',emoji:'•',accent:'#31c78f'}
};
const scopeZoom={country:5,county:9,city:12,zone:15};
const scopeRadius={country:700,county:100,city:24,zone:4};
let state={lat:45.9432,lon:24.9668,zoom:6,scope:'city',areaName:'Romania',selectedCat:'suspicious',filters:new Set(Object.keys(categories)),adding:false};
const demoReports=[
 {id:'d1',lat:44.432,lon:26.103,cat:'suspicious',title:'Demo: unusual activity',details:'Example report used only to demonstrate the interface.',time:'Demo data',demo:true},
 {id:'d2',lat:45.697,lon:27.184,cat:'paranormal',title:'Demo: unexplained lights',details:'Example paranormal report. This is not a real incident.',time:'Demo data',demo:true},
 {id:'d3',lat:46.770,lon:23.590,cat:'safety',title:'Demo: safety issue',details:'Example map marker. No real-world claim is being made.',time:'Demo data',demo:true},
 {id:'d4',lat:47.160,lon:27.588,cat:'crime',title:'Demo: reported theft',details:'Fictional demo entry for layout testing only.',time:'Demo data',demo:true}
];
let userReports=[];
try{userReports=JSON.parse(localStorage.getItem('areapulse.reports')||'[]')}catch(e){userReports=[]}
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const map=$('#map'), tiles=$('#tiles'), markers=$('#markers'), toastEl=$('#toast');
function clamp(v,a,b){return Math.max(a,Math.min(b,v))}
function lonToX(lon,z){return (lon+180)/360*TILE*Math.pow(2,z)}
function latToY(lat,z){lat=clamp(lat,-85.05112878,85.05112878);const r=lat*Math.PI/180;return (1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*TILE*Math.pow(2,z)}
function xToLon(x,z){return x/(TILE*Math.pow(2,z))*360-180}
function yToLat(y,z){const n=Math.PI-2*Math.PI*y/(TILE*Math.pow(2,z));return 180/Math.PI*Math.atan(.5*(Math.exp(n)-Math.exp(-n)))}
function wrappedX(x,n){return ((x%n)+n)%n}
function distanceKm(a,b,c,d){const R=6371, p=Math.PI/180;const x=(c-a)*p,y=(d-b)*p;const A=Math.sin(x/2)**2+Math.cos(a*p)*Math.cos(c*p)*Math.sin(y/2)**2;return 2*R*Math.asin(Math.sqrt(A))}
function render(){renderTiles();renderMarkers();updateUI()}
let raf=0; function schedule(){if(!raf)raf=requestAnimationFrame(()=>{raf=0;render()})}
function renderTiles(){
  const w=map.clientWidth,h=map.clientHeight,z=state.zoom,n=Math.pow(2,z),cx=lonToX(state.lon,z),cy=latToY(state.lat,z);
  const minX=Math.floor((cx-w/2)/TILE)-1,maxX=Math.floor((cx+w/2)/TILE)+1,minY=Math.floor((cy-h/2)/TILE)-1,maxY=Math.floor((cy+h/2)/TILE)+1;
  const frag=document.createDocumentFragment();tiles.textContent='';
  for(let ty=minY;ty<=maxY;ty++){if(ty<0||ty>=n)continue;for(let tx=minX;tx<=maxX;tx++){
    const img=document.createElement('img');img.className='tile';img.alt='';img.draggable=false;img.decoding='async';
    const tileX=wrappedX(tx,n);
    const fallback='data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="256" height="256"%3E%3Crect width="256" height="256" fill="%23e3e9ef"/%3E%3Cpath d="M0 80h256M0 170h256M70 0v256M180 0v256" stroke="%23d1d8e0" stroke-width="2"/%3E%3C/svg%3E';
    img.src=`https://tile.openstreetmap.org/${z}/${tileX}/${ty}.png`;
    img.onerror=()=>{img.onerror=null;img.src=fallback};
    img.style.left=(tx*TILE-cx+w/2)+'px';img.style.top=(ty*TILE-cy+h/2)+'px';frag.appendChild(img);
  }} tiles.appendChild(frag);
}
function reportsInScope(){const all=[...demoReports,...userReports],r=scopeRadius[state.scope];return all.filter(x=>state.filters.has(x.cat)&&distanceKm(state.lat,state.lon,x.lat,x.lon)<=r)}
function renderMarkers(){
  const w=map.clientWidth,h=map.clientHeight,z=state.zoom,cx=lonToX(state.lon,z),cy=latToY(state.lat,z);markers.textContent='';
  reportsInScope().forEach(r=>{const x=lonToX(r.lon,z)-cx+w/2,y=latToY(r.lat,z)-cy+h/2;if(x<-70||x>w+70||y<-70||y>h+70)return;
    const b=document.createElement('button');b.className='marker'+(r.demo?' demo':'');b.style.left=x+'px';b.style.top=y+'px';b.innerHTML=`<span class="pin" style="box-shadow:0 8px 18px ${categories[r.cat].accent}33">${categories[r.cat].emoji}</span>`;b.onclick=e=>{e.stopPropagation();openDetail(r)};markers.appendChild(b);
  })
}
function updateUI(){
  $('#areaBadge').textContent=`${state.areaName} · ${cap(state.scope)} view · ${reportsInScope().length} shown`;
  $$('.scope').forEach(b=>b.classList.toggle('active',b.dataset.scope===state.scope));
  $('#coordText').textContent=`⌖ ${state.lat.toFixed(5)}, ${state.lon.toFixed(5)}`;
}
function cap(s){return s.charAt(0).toUpperCase()+s.slice(1)}
function setScope(scope){state.scope=scope;state.zoom=scopeZoom[scope];schedule()}
$$('.scope').forEach(b=>b.onclick=()=>setScope(b.dataset.scope));
$('#zoomIn').onclick=()=>{state.zoom=clamp(state.zoom+1,MIN_Z,MAX_Z);schedule()};
$('#zoomOut').onclick=()=>{state.zoom=clamp(state.zoom-1,MIN_Z,MAX_Z);schedule()};
$('#locationBtn').onclick=()=>{
  if(!navigator.geolocation){toast('Location is not available on this device.');return}
  toast('Getting your location…');
  navigator.geolocation.getCurrentPosition(pos=>{
    state.lat=pos.coords.latitude;state.lon=pos.coords.longitude;state.zoom=15;state.scope='zone';state.areaName='Current area';schedule();toast('Centered on your location.');
  },err=>{
    const denied=err&&err.code===1;toast(denied?'Location permission was not granted.':'Could not get your location. Try search instead.');
  },{enableHighAccuracy:false,timeout:10000,maximumAge:120000});
};
$('#homeBtn').onclick=()=>{state.lat=45.9432;state.lon=24.9668;state.zoom=6;state.areaName='Romania';state.scope='city';schedule()};
let drag=null,moved=false;
map.addEventListener('pointerdown',e=>{if(e.target.closest('button'))return;map.setPointerCapture(e.pointerId);drag={id:e.pointerId,x:e.clientX,y:e.clientY,cx:lonToX(state.lon,state.zoom),cy:latToY(state.lat,state.zoom)};moved=false});
map.addEventListener('pointermove',e=>{if(!drag||e.pointerId!==drag.id)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>4)moved=true;const nx=drag.cx-dx,ny=drag.cy-dy;state.lon=xToLon(nx,state.zoom);state.lat=clamp(yToLat(ny,state.zoom),-85,85);schedule()});
map.addEventListener('pointerup',e=>{if(drag&&e.pointerId===drag.id)drag=null});
map.addEventListener('wheel',e=>{e.preventDefault();state.zoom=clamp(state.zoom+(e.deltaY<0?1:-1),MIN_Z,MAX_Z);schedule()},{passive:false});
let lastTap=0;map.addEventListener('pointerup',()=>{const now=Date.now();if(now-lastTap<300){state.zoom=clamp(state.zoom+1,MIN_Z,MAX_Z);schedule()}lastTap=now});
function openSheet(id){$$('.sheet').forEach(x=>x.classList.remove('open'));$('#'+id).classList.add('open')}
function closeSheet(id){$('#'+id).classList.remove('open');if(id==='addSheet'){state.adding=false;$('#crosshair').style.display='none'}}
$$('[data-close]').forEach(b=>b.onclick=()=>closeSheet(b.dataset.close));
function toast(msg){toastEl.textContent=msg;toastEl.classList.add('show');clearTimeout(toast._t);toast._t=setTimeout(()=>toastEl.classList.remove('show'),2200)}
function buildCats(){
 const g=$('#catGrid');g.textContent='';Object.entries(categories).forEach(([k,c])=>{const b=document.createElement('button');b.className='cat'+(k===state.selectedCat?' selected':'');b.innerHTML=`<span class="em">${c.emoji}</span><span>${c.label}</span>`;b.onclick=()=>{state.selectedCat=k;buildCats()};g.appendChild(b)});
 const f=$('#filterGrid');f.textContent='';Object.entries(categories).forEach(([k,c])=>{const b=document.createElement('button');b.className='cat'+(state.filters.has(k)?' selected':'');b.innerHTML=`<span class="em">${c.emoji}</span><span>${c.label}</span>`;b.onclick=()=>{state.filters.has(k)?state.filters.delete(k):state.filters.add(k);buildCats();schedule()};f.appendChild(b)});
}
buildCats();
$('#addBtn').onclick=()=>{state.adding=true;$('#crosshair').style.display='block';openSheet('addSheet');updateUI()};
$('#reportsTab').onclick=()=>{renderReportList();openSheet('reportsSheet')};
$('#filterBtn').onclick=()=>openSheet('filterSheet');$('#infoBtn').onclick=()=>openSheet('infoSheet');
$('#mapTab').onclick=()=>$$('.sheet').forEach(s=>s.classList.remove('open'));
$('#saveReport').onclick=()=>{
 const title=$('#reportTitle').value.trim(),details=$('#reportDetails').value.trim();if(!title){toast('Add a short title first');return}
 const r={id:'u'+Date.now(),lat:state.lat,lon:state.lon,cat:state.selectedCat,title,details:details||'No additional details.',time:new Date().toLocaleString(),demo:false};
 userReports.unshift(r);localStorage.setItem('areapulse.reports',JSON.stringify(userReports));$('#reportTitle').value='';$('#reportDetails').value='';closeSheet('addSheet');schedule();toast('Report saved on this device');
};
function renderReportList(){const list=$('#reportList'),arr=reportsInScope();$('#reportScopeText').textContent=`${cap(state.scope)} scope around ${state.areaName}. Community entries are unverified.`;list.textContent='';if(!arr.length){list.innerHTML='<div class="empty">No reports match this map area and filter.</div>';return}
 arr.sort((a,b)=>Number(b.id.slice(1)||0)-Number(a.id.slice(1)||0)).forEach(r=>{const d=document.createElement('button');d.className='reportCard';d.style.width='100%';d.style.textAlign='left';d.style.borderColor='#e3e6ed';d.innerHTML=`<div class="reportTop"><span class="tag">${categories[r.cat].emoji} ${categories[r.cat].label}</span><span class="tag">${r.demo?'DEMO':'LOCAL'}</span></div><h3>${esc(r.title)}</h3><p>${esc(r.details)}</p><div class="reportMeta">${esc(r.time)} · ${distanceKm(state.lat,state.lon,r.lat,r.lon).toFixed(1)} km from map center</div>`;d.onclick=()=>openDetail(r);list.appendChild(d)})
}
function openDetail(r){$('#detailTitle').textContent=r.title;$('#detailBody').innerHTML=`<div class="reportCard"><div class="reportTop"><span class="tag">${categories[r.cat].emoji} ${categories[r.cat].label}</span><span class="tag">${r.demo?'DEMO':'LOCAL · UNVERIFIED'}</span></div><p style="margin-top:12px">${esc(r.details)}</p><div class="reportMeta">${esc(r.time)}<br>${r.lat.toFixed(5)}, ${r.lon.toFixed(5)}</div></div>${r.demo?'':'<button class="primary" id="deleteCurrent" style="background:#7b2936">Delete this local report</button>'}`;openSheet('detailSheet');if(!r.demo){$('#deleteCurrent').onclick=()=>{userReports=userReports.filter(x=>x.id!==r.id);localStorage.setItem('areapulse.reports',JSON.stringify(userReports));closeSheet('detailSheet');schedule();toast('Report deleted')}}}
function esc(s){return String(s).replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]))}
let lastSearch=0;
$('#searchForm').addEventListener('submit',async e=>{e.preventDefault();const q=$('#searchInput').value.trim();if(q.length<2)return;const now=Date.now();if(now-lastSearch<1100){toast('Please wait a moment before another search');return}lastSearch=now;const box=$('#searchResults');box.style.display='block';box.innerHTML='<div class="searchItem"><b>Searching…</b><span>Explicit searches only — no autocomplete</span></div>';
 try{const key='geo:'+q.toLowerCase(),cached=localStorage.getItem(key);let data;if(cached){data=JSON.parse(cached)}else{const url='https://photon.komoot.io/api/?limit=5&q='+encodeURIComponent(q);const res=await fetch(url,{headers:{'Accept-Language':navigator.language||'en'}});if(!res.ok)throw new Error('search');const json=await res.json();data=json.features||[];localStorage.setItem(key,JSON.stringify(data))}showSearch(data,q)}catch(err){box.innerHTML='<button class="searchItem" type="button"><b>Search unavailable</b><span>Move the map manually or try again with an internet connection.</span></button>'}
});
function showSearch(data,q){const box=$('#searchResults');box.textContent='';if(!data.length){box.innerHTML='<div class="searchItem"><b>No place found</b><span>Try a city, county/region or country name.</span></div>';return}data.forEach(r=>{const b=document.createElement('button');b.className='searchItem';const a=r.properties||{},coords=(r.geometry&&r.geometry.coordinates)||[];const main=a.name||a.city||a.county||a.state||a.country||q;const parts=[a.name,a.city,a.county,a.state,a.country].filter((x,i,arr)=>x&&arr.indexOf(x)===i);const display=parts.join(', ');b.innerHTML=`<b>${esc(main)}</b><span>${esc(display||q)}</span>`;b.onclick=()=>{state.lon=parseFloat(coords[0]);state.lat=parseFloat(coords[1]);if(!Number.isFinite(state.lat)||!Number.isFinite(state.lon))return;state.areaName=main;state.zoom=scopeZoom[state.scope];box.style.display='none';$('#searchInput').value=main;schedule()};box.appendChild(b)})}
document.addEventListener('pointerdown',e=>{if(!e.target.closest('.searchWrap'))$('#searchResults').style.display='none'});
window.addEventListener('resize',schedule);render();
})();
