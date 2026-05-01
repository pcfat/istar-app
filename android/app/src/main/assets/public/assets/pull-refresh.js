// Pull-to-refresh indicator
(function(){
var c=document.createElement('div');
c.innerHTML='<div id="_pci" style="position:absolute;top:0;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:center;z-index:2147483647;pointer-events:none;opacity:0;background:linear-gradient(135deg,#2196F3,#64B5F6);"><div style="width:24px;height:24px;border-radius:50%;border:3px solid rgba(255,255,255,0.4);border-top-color:white;transform:scale(0);transition:transform 0.15s;"></div><span style="color:white;margin-left:10px;font-size:13px;font-weight:500;opacity:0;">釋放以重新整理</span></div>';
document.body.appendChild(c);
var s=document.querySelector('#_pci');
var sp=document.querySelector('#_pci>div');
var tx=document.querySelector('#_pci>span');
window.__setPullProgress=function(p){
  if(!s)return;
  if(p<0.08){s.style.opacity='0';sp.style.transform='scale(0)';tx.style.opacity='0';}
  else{s.style.opacity='1';sp.style.transform='scale('+Math.min(p,1)+')';tx.style.opacity=(p>0.4)?'1':'0';if(p>=1){sp.style.transform='scale(1)';sp.style.borderTopColor='transparent';sp.style.borderRightColor='white';sp.style.animation='_spin 0.6s linear infinite';}}
};
window.__setRefreshing=function(v){
  if(!s)return;
  if(v){s.style.opacity='1';sp.style.transform='scale(1)';sp.style.borderTopColor='transparent';sp.style.borderRightColor='white';sp.style.animation='_spin 0.6s linear infinite';tx.style.opacity='1';tx.textContent='正在重新整理...';}
  else{s.style.opacity='0';sp.style.transform='scale(0)';tx.textContent='釋放以重新整理';setTimeout(function(){sp.style.animation='';sp.style.borderTopColor='white';sp.style.borderRightColor='transparent';tx.style.opacity='0';},300);}
};
var st=document.createElement('style');
st.textContent='@keyframes _spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}';
document.head.appendChild(st);
})();