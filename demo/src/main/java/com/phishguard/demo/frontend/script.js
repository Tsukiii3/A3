const API = 'http://localhost:8080';
const TOKEN = localStorage.getItem('phishguard_token');
 
/* Se não tiver token, vai pro login */
if (!TOKEN) window.location.href = 'login.html';
 
/* ── HELPERS ── */
const avatarColors = ['#ea4335','#1a73e8','#1e8e3e','#f6bf26','#9c27b0','#00bcd4','#ff5722','#607d8b'];
function strColor(s) { let h=0; for(let c of s) h=(h*31+c.charCodeAt(0))%avatarColors.length; return avatarColors[h]; }
function initials(name) { return name.split(' ').slice(0,2).map(w=>w[0]).join('').toUpperCase(); }
 
function toast(msg, type='info') {
  const colors = { info:'#1a73e8', success:'#1e8e3e', danger:'#c5221f', warning:'#b06000' };
  const t = document.createElement('div');
  t.style.cssText = `position:fixed;bottom:24px;left:50%;transform:translateX(-50%) translateY(80px);
    background:${colors[type]||colors.info};color:#fff;padding:10px 22px;border-radius:8px;
    font-size:13px;font-weight:500;z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,0.25);
    transition:transform 0.25s cubic-bezier(0.4,0,0.2,1),opacity 0.25s;opacity:0;pointer-events:none;
    font-family:'DM Sans',sans-serif;`;
  t.textContent = msg;
  document.body.appendChild(t);
  requestAnimationFrame(() => { t.style.transform='translateX(-50%) translateY(0)'; t.style.opacity='1'; });
  setTimeout(() => { t.style.transform='translateX(-50%) translateY(80px)'; t.style.opacity='0';
    setTimeout(() => t.remove(), 300); }, 2600);
}
 
/* ── API ── */
async function apiFetch(path, options = {}) {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${TOKEN}`,
      ...(options.headers || {})
    }
  });
 
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('phishguard_token');
    window.location.href = 'login.html';
    return null;
  }
 
  return res;
}
 
/* ── BADGE DE CLASSIFICAÇÃO ── */
function badgePhishing(classificacao, score) {
  if (!classificacao) return '';
  const cfg = {
    SEGURO:   { bg: '#e6f4ea', color: '#1e8e3e', icon: '✓' },
    SUSPEITO: { bg: '#fef7e0', color: '#b06000', icon: '⚠' },
    FRAUDE:   { bg: '#fce8e6', color: '#c5221f', icon: '✕' },
  }[classificacao] || { bg: '#f1f3f4', color: '#5f6368', icon: '?' };
 
  return `<span style="
    display:inline-flex;align-items:center;gap:4px;
    background:${cfg.bg};color:${cfg.color};
    font-size:11px;font-weight:600;
    padding:2px 8px;border-radius:12px;
    margin-left:8px;vertical-align:middle;">
    ${cfg.icon} ${classificacao} ${score != null ? `· ${score}` : ''}
  </span>`;
}
 
/* ── DATA ── */
let allEmails = [];
let isLoading = false;
 
/* Converte resposta da API pro formato do frontend */
function apiEmailParaLocal(apiEmail, index) {
  const fromRaw = apiEmail.from || '';
  const nomeMatch = fromRaw.match(/^(.+?)\s*</);
  const addrMatch = fromRaw.match(/<(.+?)>/);
  const nome = nomeMatch ? nomeMatch[1].trim() : fromRaw.replace(/<.*>/, '').trim();
  const addr = addrMatch ? addrMatch[1] : fromRaw;
 
  return {
    id:             index + 1,
    folder:         'inbox',
    from:           nome || addr,
    addr:           addr,
    subject:        apiEmail.subject || '(sem assunto)',
    preview:        apiEmail.body ? apiEmail.body.substring(0, 100).replace(/\n/g, ' ') : '',
    body:           apiEmail.body || '',
    date:           'agora',
    unread:         true,
    starred:        false,
    classificacao:  apiEmail.classificacao,
    score:          apiEmail.score,
    motivos:        apiEmail.motivos || [],
  };
}
 
/* Carrega emails do backend */
async function carregarEmails() {
  if (isLoading) return;
  isLoading = true;
 
  setLoadingState(true);
 
  try {
    const res = await apiFetch('/api/emails/analisar');
    if (!res) return;
 
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.erro || 'Erro ao carregar emails');
    }
 
    const data = await res.json();
 
    /* Mantém emails locais (sent, drafts) e substitui inbox */
    const naoInbox = allEmails.filter(e => e.folder !== 'inbox');
    const novosInbox = data.map((e, i) => apiEmailParaLocal(e, i));
 
    allEmails = [...novosInbox, ...naoInbox];
 
    renderEmails();
    refreshBadges();
    atualizarContador();
    toast(`${novosInbox.length} email(s) analisados`, 'success');
 
  } catch (err) {
    console.error(err);
    toast('Erro ao carregar emails: ' + err.message, 'danger');
  } finally {
    isLoading = false;
    setLoadingState(false);
  }
}
 
function setLoadingState(on) {
  const btn = document.getElementById('refreshBtn');
  const svg = btn?.querySelector('svg');
  if (!svg) return;
  if (on) {
    svg.style.transition = 'transform 1s linear infinite';
    svg.style.animation = 'spin 1s linear infinite';
    const style = document.getElementById('spin-style') || document.createElement('style');
    style.id = 'spin-style';
    style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
    document.head.appendChild(style);
  } else {
    svg.style.animation = '';
    svg.style.transform = '';
  }
}
 
function atualizarContador() {
  const inbox = allEmails.filter(e => e.folder === 'inbox');
  const el = document.querySelector('.cat-count');
  if (el) el.textContent = inbox.length;
  const pageInfo = document.getElementById('pageInfo');
  if (pageInfo) pageInfo.textContent = `1–${inbox.length} de ${inbox.length}`;
}
 
/* ── AVATAR DO USUÁRIO LOGADO ── */
function inicializarUsuario() {
  const nome  = localStorage.getItem('phishguard_nome') || '';
  const email = localStorage.getItem('phishguard_email') || '';
  const av    = document.querySelector('.avatar');
  if (!av) return;
 
  const ini = nome
    ? nome.split(' ').slice(0,2).map(w=>w[0]).join('').toUpperCase()
    : email.substring(0,2).toUpperCase();
 
  av.textContent = ini;
  av.title = nome || email;
  av.style.cursor = 'pointer';
  av.addEventListener('click', () => {
    if (confirm(`Sair da conta ${email}?`)) {
      localStorage.clear();
      window.location.href = 'login.html';
    }
  });
}
 
/* ── STATE ── */
let currentFolder = 'inbox';
let openEmailId   = null;
let selectedIds   = new Set();
let searchQuery   = '';
let composeMin    = false;
 
/* ── SETTINGS STATE ── */
let settings = {
  dark: false, fontSize: 14, density: 'default',
  accent: '#1a73e8', autoRead: true, readingPane: true,
  notif: false, sound: false,
};
 
/* ── DERIVED ── */
function getVisible() {
  let list = allEmails.filter(e => {
    if (currentFolder === 'starred') return e.starred;
    return e.folder === currentFolder;
  });
  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    list = list.filter(e =>
      e.from.toLowerCase().includes(q) ||
      e.subject.toLowerCase().includes(q) ||
      e.preview.toLowerCase().includes(q)
    );
  }
  return list;
}
 
function unreadCount(folder) {
  if (folder === 'starred') return allEmails.filter(e => e.starred && e.unread).length;
  return allEmails.filter(e => e.folder === folder && e.unread).length;
}
 
/* ── BADGES ── */
function refreshBadges() {
  document.querySelectorAll('.nav-item[data-folder]').forEach(el => {
    const f = el.dataset.folder;
    const badge = el.querySelector('.nav-badge');
    if (!badge) return;
    const count = unreadCount(f);
    badge.textContent = count || '';
    badge.style.display = count ? '' : 'none';
  });
}
 
/* ── RENDER ── */
function renderEmails(list) {
  const el = document.getElementById('emailList');
  if (!list) list = getVisible();
 
  if (!list.length) {
    el.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:280px;color:var(--text-muted);gap:12px;">
      <svg width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg>
      <span style="font-size:14px;">Nenhuma mensagem</span>
    </div>`;
    return;
  }
 
  const rowH = { compact: '40px', default: '52px', comfortable: '64px' }[settings.density];
 
  el.innerHTML = '';
  list.forEach(email => {
    const isSel = selectedIds.has(email.id);
    const row = document.createElement('div');
    row.className = 'email-row' + (email.unread?'':' read') + (openEmailId===email.id?' open':'') + (isSel?' selected':'');
    row.dataset.id = email.id;
    row.style.height = rowH;
 
    /* Badge de phishing na linha */
    const phishBadge = email.classificacao ? badgePhishing(email.classificacao, email.score) : '';
 
    row.innerHTML = `
      <div class="row-check">
        <div class="checkbox${isSel?' checked':''}" data-check="${email.id}">
          ${isSel?'<svg width="12" height="12" fill="none" stroke="white" stroke-width="3" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>':''}
        </div>
      </div>
      <div class="row-star">
        <button class="star-btn${email.starred?' starred':''}" data-star="${email.id}">
          <svg width="16" height="16" fill="${email.starred?'currentColor':'none'}" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
        </button>
      </div>
      <div class="row-sender">${email.from}</div>
      <div class="row-body">
        <span class="row-subject">${email.subject}${phishBadge}</span>
        <span class="row-preview">— ${email.preview}</span>
      </div>
      <div class="row-meta">
        ${email.unread ? '<div class="unread-dot"></div>' : ''}
        <span class="row-date">${email.date}</span>
        <div class="row-actions">
          <button class="row-action-btn" data-action="archive" data-id="${email.id}" title="Arquivar">
            <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>
          </button>
          <button class="row-action-btn" data-action="delete" data-id="${email.id}" title="Excluir">
            <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
          </button>
          <button class="row-action-btn" data-action="markread" data-id="${email.id}" title="${email.unread?'Marcar como lido':'Marcar como não lido'}">
            <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
        </div>
      </div>`;
 
    row.addEventListener('click', e => {
      if (e.target.closest('[data-check]')||e.target.closest('[data-star]')||e.target.closest('[data-action]')) return;
      openEmail(email);
    });
    row.querySelector('[data-check]').addEventListener('click', ev => { ev.stopPropagation(); toggleSelect(email.id); });
    row.querySelector('[data-star]').addEventListener('click',  ev => { ev.stopPropagation(); toggleStar(email.id); });
    row.querySelectorAll('[data-action]').forEach(btn => {
      btn.addEventListener('click', ev => {
        ev.stopPropagation();
        const id = parseInt(btn.dataset.id);
        if (btn.dataset.action==='delete')   deleteEmail(id);
        if (btn.dataset.action==='archive')  archiveEmail(id);
        if (btn.dataset.action==='markread') toggleRead(id);
      });
    });
 
    el.appendChild(row);
  });
}
 
/* ── SELECTION ── */
function toggleSelect(id) {
  selectedIds.has(id) ? selectedIds.delete(id) : selectedIds.add(id);
  renderEmails(); updateSelectAll();
}
function updateSelectAll() {
  const cb = document.getElementById('selectAll');
  const vis = getVisible();
  const all = vis.length>0 && vis.every(e=>selectedIds.has(e.id));
  cb.classList.toggle('checked', all);
  cb.innerHTML = all?'<svg width="12" height="12" fill="none" stroke="white" stroke-width="3" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>':'';
}
document.getElementById('selectAll').addEventListener('click', () => {
  const vis = getVisible();
  const all = vis.every(e=>selectedIds.has(e.id));
  vis.forEach(e => all ? selectedIds.delete(e.id) : selectedIds.add(e.id));
  renderEmails(); updateSelectAll();
});
 
/* ── STAR ── */
function toggleStar(id) {
  const email = allEmails.find(e=>e.id===id);
  if (!email) return;
  email.starred = !email.starred;
  renderEmails(); refreshBadges();
}
 
/* ── READ ── */
function toggleRead(id) {
  const email = allEmails.find(e=>e.id===id);
  if (!email) return;
  email.unread = !email.unread;
  renderEmails(); refreshBadges();
  toast(email.unread ? 'Marcado como não lido' : 'Marcado como lido');
}
function markAsRead(id) {
  const email = allEmails.find(e=>e.id===id);
  if (email && email.unread && settings.autoRead) { email.unread=false; refreshBadges(); }
}
 
/* ── DELETE ── */
function deleteEmail(id) {
  const idx = allEmails.findIndex(e=>e.id===id);
  if (idx===-1) return;
  allEmails.splice(idx,1);
  selectedIds.delete(id);
  if (openEmailId===id) closeViewer();
  renderEmails(); refreshBadges();
  toast('Mensagem excluída','danger');
}
function deleteSelected() {
  if (!selectedIds.size) return;
  const count = selectedIds.size;
  [...selectedIds].forEach(id => {
    const idx = allEmails.findIndex(e=>e.id===id);
    if (idx!==-1) allEmails.splice(idx,1);
    if (openEmailId===id) closeViewer();
  });
  selectedIds.clear();
  renderEmails(); refreshBadges();
  toast(`${count} mensagem(s) excluída(s)`,'danger');
}
 
/* ── ARCHIVE ── */
function archiveEmail(id) {
  const email = allEmails.find(e=>e.id===id);
  if (!email) return;
  email.folder='archive';
  selectedIds.delete(id);
  if (openEmailId===id) closeViewer();
  renderEmails(); refreshBadges();
  toast('Mensagem arquivada');
}
 
/* ── VIEWER ── */
function openEmail(email) {
  openEmailId = email.id;
  markAsRead(email.id);
  document.getElementById('viewerTitle').textContent  = email.subject;
  document.getElementById('viewerSender').textContent = email.from;
  document.getElementById('viewerAddr').textContent   = `<${email.addr}>`;
  document.getElementById('viewerDate').textContent   = email.date;
 
  /* Body + painel de análise phishing */
  const bodyEl = document.getElementById('viewerBody');
  const bodyHtml = email.body.split('\n').map(l=>l.trim()?`<p>${l}</p>`:'<p>&nbsp;</p>').join('');
 
  let phishPanel = '';
  if (email.classificacao) {
    const cfg = {
      SEGURO:   { bg:'#e6f4ea', border:'#34a853', color:'#1e8e3e', title:'Email seguro' },
      SUSPEITO: { bg:'#fef7e0', border:'#fbbc04', color:'#b06000', title:'Email suspeito' },
      FRAUDE:   { bg:'#fce8e6', border:'#ea4335', color:'#c5221f', title:'Possível fraude!' },
    }[email.classificacao] || {};
 
    const motivosHtml = email.motivos.length
      ? email.motivos.map(m=>`<li style="margin:4px 0;font-size:13px;">${m}</li>`).join('')
      : '<li style="font-size:13px;color:var(--text-muted)">Sem indicadores detectados</li>';
 
    phishPanel = `
      <div style="
        background:${cfg.bg};
        border-left:4px solid ${cfg.border};
        border-radius:8px;
        padding:14px 16px;
        margin-bottom:20px;">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
          <strong style="color:${cfg.color};font-size:14px;">${cfg.title}</strong>
          <span style="color:${cfg.color};font-size:13px;opacity:0.8;">Score: ${email.score}/100</span>
        </div>
        <ul style="padding-left:18px;margin:0;color:var(--text-secondary);">
          ${motivosHtml}
        </ul>
      </div>`;
  }
 
  bodyEl.innerHTML = phishPanel + bodyHtml;
 
  const av = document.getElementById('viewerAvatar');
  av.textContent=initials(email.from); av.style.background=strColor(email.from);
  const svg = document.querySelector('#emailViewer .icon-btn[title="Com estrela"] svg');
  if (svg) svg.setAttribute('fill', email.starred?'currentColor':'none');
  document.getElementById('emailViewer').classList.add('open');
  renderEmails();
}
 
function closeViewer() {
  document.getElementById('emailViewer').classList.remove('open');
  openEmailId=null; renderEmails();
}
 
document.getElementById('closeViewer').addEventListener('click', closeViewer);
document.querySelector('#emailViewer .icon-btn[title="Com estrela"]').addEventListener('click', ()=>{ if(openEmailId) toggleStar(openEmailId); });
document.querySelector('#emailViewer .icon-btn[title="Excluir"]').addEventListener('click',    ()=>{ if(openEmailId) deleteEmail(openEmailId); });
document.querySelector('#emailViewer .icon-btn[title="Arquivo"]').addEventListener('click',    ()=>{ if(openEmailId) archiveEmail(openEmailId); });
 
/* ── COMPOSE ── */
function openCompose(mode) {
  composeMin = false;
  const modal = document.getElementById('composeModal');
  modal.style.height = '';
  modal.classList.add('open');
  if (mode==='reply' && openEmailId) {
    const e = allEmails.find(x=>x.id===openEmailId);
    if (e) { document.getElementById('composeTo').value=e.addr; document.getElementById('composeSubject').value='Re: '+e.subject; document.getElementById('composeBody').value=''; document.getElementById('composeBody').focus(); }
  } else if (mode==='forward' && openEmailId) {
    const e = allEmails.find(x=>x.id===openEmailId);
    if (e) { document.getElementById('composeTo').value=''; document.getElementById('composeSubject').value='Fwd: '+e.subject; document.getElementById('composeBody').value='\n\n--- Mensagem encaminhada ---\nDe: '+e.from+'\nAssunto: '+e.subject+'\n\n'+e.body; document.getElementById('composeTo').focus(); }
  } else {
    document.getElementById('composeTo').value=''; document.getElementById('composeSubject').value=''; document.getElementById('composeBody').value='';
    document.getElementById('composeTo').focus();
  }
}
 
document.getElementById('composeBtn').addEventListener('click', ()=>openCompose('new'));
document.getElementById('replyBtn').addEventListener('click',   ()=>openCompose('reply'));
document.getElementById('forwardBtn').addEventListener('click', ()=>openCompose('forward'));
document.getElementById('closeCompose').addEventListener('click', ()=>{ document.getElementById('composeModal').classList.remove('open'); composeMin=false; });
document.getElementById('minimizeCompose').addEventListener('click', ()=>{
  composeMin = !composeMin;
  document.getElementById('composeModal').style.height = composeMin?'44px':'';
});
 
document.getElementById('sendBtn').addEventListener('click', ()=>{
  const to=document.getElementById('composeTo').value.trim();
  const sub=document.getElementById('composeSubject').value.trim();
  const body=document.getElementById('composeBody').value.trim();
  if (!to)  { toast('Informe o destinatário','warning'); return; }
  if (!sub) { toast('Informe o assunto','warning'); return; }
  if (!body){ toast('O corpo está vazio','warning'); return; }
  const newId = Math.max(0, ...allEmails.map(e=>e.id))+1;
  allEmails.push({id:newId,folder:'sent',from:'Você',addr:to,subject:sub,preview:body.substring(0,80),body,date:'agora',unread:false,starred:false});
  document.getElementById('composeModal').classList.remove('open');
  toast('Mensagem enviada!','success');
  if (currentFolder==='sent') renderEmails();
  refreshBadges();
});
 
document.getElementById('discardBtn').addEventListener('click', ()=>{
  document.getElementById('composeModal').classList.remove('open');
  toast('Rascunho descartado','danger');
});
 
/* ── REFRESH — chama a API ── */
document.getElementById('refreshBtn').addEventListener('click', () => {
  carregarEmails();
});
 
/* ── TOOLBAR BULK ── */
document.querySelectorAll('.tb-btn').forEach(btn=>{
  const t=btn.textContent.trim();
  if(t==='Excluir') btn.addEventListener('click',deleteSelected);
  if(t==='Arquivo') btn.addEventListener('click',()=>{ if(!selectedIds.size) return; const c=selectedIds.size; [...selectedIds].forEach(id=>archiveEmail(id)); selectedIds.clear(); toast(`${c} mensagem(s) arquivada(s)`); });
  if(t==='Spam')    btn.addEventListener('click',()=>{ if(!selectedIds.size&&!openEmailId){toast('Selecione uma mensagem','warning');return;} [...selectedIds].forEach(id=>deleteEmail(id)); if(openEmailId) deleteEmail(openEmailId); toast('Marcado como spam','warning'); });
  if(t==='Filtrar') btn.addEventListener('click',()=>toast('Filtros em breve','info'));
});
 
/* ── FOLDERS ── */
document.querySelectorAll('.nav-item[data-folder]').forEach(item=>{
  item.addEventListener('click',()=>{
    document.querySelector('.nav-item.active')?.classList.remove('active');
    item.classList.add('active');
    currentFolder=item.dataset.folder;
    selectedIds.clear(); closeViewer(); renderEmails();
  });
});
 
/* ── SEARCH ── */
let searchTimer;
document.querySelector('.search-input').addEventListener('input', e=>{
  clearTimeout(searchTimer);
  searchTimer=setTimeout(()=>{ searchQuery=e.target.value.trim(); renderEmails(); },220);
});
document.querySelector('.search-input').addEventListener('keydown',e=>{
  if(e.key==='Escape'){ e.target.value=''; searchQuery=''; renderEmails(); e.target.blur(); }
});
 
/* ── KEYBOARD ── */
document.addEventListener('keydown',e=>{
  const tag=document.activeElement.tagName;
  if(tag==='INPUT'||tag==='TEXTAREA') return;
  if(e.key==='c'||e.key==='C') openCompose('new');
  if(e.key==='Escape'&&openEmailId) closeViewer();
  if((e.key==='Delete'||e.key==='Backspace')&&openEmailId) deleteEmail(openEmailId);
});
 
/* ════════════════════════════════════════
   SETTINGS
   ════════════════════════════════════════ */
function openSettings() { syncSettingsUI(); document.getElementById('settingsPanel').classList.add('open'); document.getElementById('settingsOverlay').classList.add('open'); }
function closeSettings() { document.getElementById('settingsPanel').classList.remove('open'); document.getElementById('settingsOverlay').classList.remove('open'); }
 
document.getElementById('settingsBtn').addEventListener('click', openSettings);
document.getElementById('closeSettings').addEventListener('click', closeSettings);
document.getElementById('settingsOverlay').addEventListener('click', closeSettings);
 
function applySettings() {
  document.body.classList.toggle('dark', settings.dark);
  document.documentElement.style.setProperty('--font-size', settings.fontSize+'px');
  const acc = settings.accent;
  document.documentElement.style.setProperty('--accent', acc);
  document.documentElement.style.setProperty('--accent-hover', shadeColor(acc,-20));
  document.documentElement.style.setProperty('--accent-light', hexToRgba(acc,0.12));
  document.documentElement.style.setProperty('--active-text', acc);
  document.documentElement.style.setProperty('--active-bg', hexToRgba(acc,0.12));
  renderEmails();
}
function shadeColor(hex,pct){ const n=parseInt(hex.replace('#',''),16); const r=Math.min(255,Math.max(0,(n>>16)+pct)); const g=Math.min(255,Math.max(0,((n>>8)&0xff)+pct)); const b=Math.min(255,Math.max(0,(n&0xff)+pct)); return '#'+[r,g,b].map(x=>x.toString(16).padStart(2,'0')).join(''); }
function hexToRgba(hex,a){ const n=parseInt(hex.replace('#',''),16); return `rgba(${n>>16},${(n>>8)&0xff},${n&0xff},${a})`; }
 
function syncSettingsUI() {
  document.getElementById('darkToggle').checked     = settings.dark;
  document.getElementById('fontSlider').value       = settings.fontSize;
  document.getElementById('autoReadToggle').checked = settings.autoRead;
  document.getElementById('readingPaneToggle').checked = settings.readingPane;
  document.getElementById('notifToggle').checked    = settings.notif;
  document.getElementById('soundToggle').checked    = settings.sound;
  updateFontPreview();
  document.querySelectorAll('.density-opt').forEach(el=>el.classList.toggle('active',el.dataset.density===settings.density));
  document.querySelectorAll('.color-swatch').forEach(el=>el.classList.toggle('active',el.dataset.color===settings.accent));
}
function updateFontPreview() {
  const sz=document.getElementById('fontSlider').value;
  const p=document.getElementById('fontPreview');
  if(p) p.style.fontSize=sz+'px';
}
 
document.getElementById('darkToggle').addEventListener('change',e=>{ settings.dark=e.target.checked; applySettings(); });
document.getElementById('fontSlider').addEventListener('input',e=>{ settings.fontSize=parseInt(e.target.value); updateFontPreview(); applySettings(); });
document.querySelectorAll('.density-opt').forEach(el=>{ el.addEventListener('click',()=>{ settings.density=el.dataset.density; document.querySelectorAll('.density-opt').forEach(x=>x.classList.remove('active')); el.classList.add('active'); renderEmails(); }); });
document.querySelectorAll('.color-swatch').forEach(el=>{ el.addEventListener('click',()=>{ settings.accent=el.dataset.color; document.querySelectorAll('.color-swatch').forEach(x=>x.classList.remove('active')); el.classList.add('active'); applySettings(); }); });
document.getElementById('autoReadToggle').addEventListener('change',e=>{ settings.autoRead=e.target.checked; });
document.getElementById('readingPaneToggle').addEventListener('change',e=>{ settings.readingPane=e.target.checked; });
document.getElementById('notifToggle').addEventListener('change',e=>{ settings.notif=e.target.checked; if(e.target.checked&&Notification.permission==='default') Notification.requestPermission(); });
document.getElementById('soundToggle').addEventListener('change',e=>{ settings.sound=e.target.checked; });
document.getElementById('saveSettings').addEventListener('click',()=>{ applySettings(); closeSettings(); toast('Configurações salvas!','success'); });
document.getElementById('resetSettings').addEventListener('click',()=>{ settings={dark:false,fontSize:14,density:'default',accent:'#1a73e8',autoRead:true,readingPane:true,notif:false,sound:false}; syncSettingsUI(); applySettings(); toast('Configurações restauradas'); });
 
/* ── INIT ── */
applySettings();
inicializarUsuario();
carregarEmails(); // carrega emails reais do backend na inicialização