// Pull-to-refresh indicator
(function(){
var c=document.createElement('div');
c.innerHTML='<div style="position:fixed;top:0;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:center;z-index:9999999;pointer-events:none;opacity:0;background:linear-gradient(135deg,#2196F3,#64B5F6);box-shadow:0 2px 8px rgba(0,0,0,0.15);font-family:-apple-system,sans-serif;"><div id="_ps" style="width:24px;height:24px;border-radius:50%;border:3px solid rgba(255,255,255,0.4);border-top-color:white;transform:scale(0);transition:transform 0.2s;"></div><span id="_pt" style="color:white;margin-left:12px;font-size:14px;font-weight:500;opacity:0;">釋放以重新整理</span></div>';
document.body.appendChild(c);
var s=document.getElementById('_ps');
var t=document.getElementById('_pt');
window.__setPullProgress=function(p){
  if(p<0.08){c.style.opacity='0';s.style.transform='scale(0)';t.style.opacity='0';}
  else{c.style.opacity='1';s.style.transform='scale('+Math.min(p,1)+')';t.style.opacity=(p>0.3)?'1':'0';if(p>=1){s.style.transform='scale(1)';s.style.borderTopColor='transparent';s.style.borderRightColor='white';s.style.animation='_spin 0.6s linear infinite';}}
};
window.__setRefreshing=function(v){
  if(v){c.style.opacity='1';s.style.transform='scale(1)';s.style.borderTopColor='transparent';s.style.borderRightColor='white';s.style.animation='_spin 0.6s linear infinite';t.style.opacity='1';t.textContent='正在重新整理...';}
  else{c.style.opacity='0';s.style.transform='scale(0)';t.textContent='釋放以重新整理';setTimeout(function(){s.style.animation='';s.style.borderTopColor='white';s.style.borderRightColor='transparent';t.style.opacity='0';},300);}
};
var st=document.createElement('style');
st.textContent='@keyframes _spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';
document.head.appendChild(st);
})();