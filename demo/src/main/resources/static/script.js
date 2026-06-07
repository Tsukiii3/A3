let settings = {
  dark: false,
  fontSize: 14,
  density: 'default',
  accent: '#1a73e8',
};

const API = window.location.hostname === 'localhost'
  ? 'http://localhost:8080'
  : 'https://a3-74um.onrender.com';
const TOKEN = localStorage.getItem('phishguard_token');

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
  if (res.status === 401) {
    localStorage.removeItem('phishguard_token');
    window.location.href = 'login.html';
    return null;
  }
  return res;
}

/* ── BADGE CLASSIFICAÇÃO (viewer) ── */
function badgePhishing(classificacao, score) {
  if (!classificacao) return '';
  const cfg = {
    SEGURO:   { bg: '#e6f4ea', color: '#1e8e3e', icon: '✓' },
    SUSPEITO: { bg: '#fff3e0', color: '#e65100', icon: '⚠' },
    FRAUDE:   { bg: '#fce8e6', color: '#c5221f', icon: '✕' },
  }[classificacao] || { bg: '#f1f3f4', color: '#5f6368', icon: '?' };
  return `<span style="display:inline-flex;align-items:center;gap:4px;background:${cfg.bg};color:${cfg.color};font-size:11px;font-weight:600;padding:2px 8px;border-radius:12px;margin-left:8px;vertical-align:middle;">${cfg.icon} ${classificacao} ${score != null ? `· ${score}` : ''}</span>`;
}

/* ── BADGE CLASSIFICAÇÃO (lista) ── */
function badgePhishingLista(classificacao) {
  if (!classificacao) {
    return `<span style="display:inline-flex;align-items:center;gap:3px;background:#f1f3f4;color:#5f6368;font-size:10px;font-weight:600;padding:1px 6px;border-radius:10px;margin-left:6px;vertical-align:middle;flex-shrink:0;">? Analisando</span>`;
  }
  const cfg = {
    SEGURO:   { bg: '#e6f4ea', color: '#1e8e3e', icon: '✓', label: 'Seguro' },
    SUSPEITO: { bg: '#fff3e0', color: '#e65100', icon: '⚠', label: 'Suspeito' },
    FRAUDE:   { bg: '#fce8e6', color: '#c5221f', icon: '✕', label: 'Fraude' },
  }[classificacao];
  if (!cfg) return '';
  return `<span style="display:inline-flex;align-items:center;gap:3px;background:${cfg.bg};color:${cfg.color};font-size:10px;font-weight:600;padding:1px 6px;border-radius:10px;margin-left:6px;vertical-align:middle;flex-shrink:0;">${cfg.icon} ${cfg.label}</span>`;
}

/* ── STATE ── */
let allEmails     = [];
let isLoading     = false;
let paginaAtual   = 0;
let temMaisEmails = false;
let totalEmails   = 0;
let totalNaoLidos = 0;
let currentFolder = 'inbox';
let currentFilter = null;
let openEmailId   = null;
let selectedIds   = new Set();
let searchQuery   = '';
let composeMin    = false;

/* ── DATA HELPERS ── */
function parseName(from) {
  const m = from?.match(/^(.+?)\s*</);
  return m ? m[1].trim() : (from || '').replace(/<.*>/, '').trim();
}
function parseAddr(from) {
  const m = from?.match(/<(.+?)>/);
  return m ? m[1] : (from || '');
}
function formatarData(iso) {
  if (!iso) return '';
  try {
    const limpo = iso.replace(/\s*\([^)]*\)\s*$/, '').trim();
    const d = new Date(limpo);
    if (isNaN(d.getTime())) return iso;
    const hoje = new Date();
    const ontem = new Date(hoje); ontem.setDate(hoje.getDate() - 1);
    if (d.toDateString() === hoje.toDateString())
      return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    if (d.toDateString() === ontem.toDateString()) return 'Ontem';
    if (d.getFullYear() === hoje.getFullYear())
      return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' });
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return ''; }
}
function emailDoServidor(e, folder) {
  const dataReal = e.dataOriginal || e.recebidoEm || '';
  return {
    id:            e.id,
    gmailId:       e.gmailId,
    folder:        folder || e.pasta || 'inbox',
    from:          parseName(e.from),
    addr:          parseAddr(e.from),
    subject:       e.subject || '(sem assunto)',
    preview:       e.body ? e.body.replace(/<[^>]*>/g, '').substring(0, 100) : '',
    body:          e.body || '',
    date:          formatarData(dataReal),
    dateRaw:       dataReal,
    unread:        !e.lido,
    starred:       e.favorito,
    classificacao: e.classificacao,
    score:         e.score,
    motivos:       e.motivos || [],
  };
}

/* ── CARREGAR EMAILS ── */
async function carregarEmails(pagina = 0) {
  if (isLoading) return;
  isLoading = true;
  setLoadingState(true);
  try {
    const res = await apiFetch(`/api/caixa/pasta/inbox?pagina=${pagina}`);
    if (!res) return;
    if (!res.ok) throw new Error('Erro ao carregar emails');
    const data   = await res.json();
    const emails = data.emails || [];
    paginaAtual   = data.pagina   ?? 0;
    temMaisEmails = data.temMais  ?? false;
    totalEmails   = data.total    ?? emails.length;
    totalNaoLidos = data.naoLidos ?? 0;
    const naoInbox = allEmails.filter(e => e.folder !== 'inbox');
    let novosInbox = emails.map(e => emailDoServidor(e, 'inbox'));
    novosInbox.sort((a, b) => new Date(b.dateRaw||0) - new Date(a.dateRaw||0));
    allEmails = [...novosInbox, ...naoInbox];
    renderEmails();
    atualizarContador();
    atualizarBotoesPagina();
  } catch (err) {
    console.error(err);
    toast('Erro ao carregar emails: ' + err.message, 'danger');
  } finally {
    isLoading = false;
    setLoadingState(false);
  }
}

function atualizarContador() {
  const inbox  = allEmails.filter(e => e.folder === 'inbox');
  const inicio = paginaAtual * 20 + 1;
  const fim    = Math.min(inicio + inbox.length - 1, totalEmails);
  const catCount = document.querySelector('.cat-count');
  if (catCount) catCount.textContent = totalEmails || inbox.length;
  const pageInfo = document.getElementById('pageInfo');
  if (pageInfo) pageInfo.textContent = totalEmails > 0 ? `${inicio}–${fim} de ${totalEmails}` : `0 de 0`;
  const badge = document.querySelector('.nav-item[data-folder="inbox"] .nav-badge');
  if (badge) {
    badge.textContent   = totalNaoLidos || '';
    badge.style.display = totalNaoLidos ? '' : 'none';
  }
}

function atualizarBotoesPagina() {
  const btnAnterior = document.querySelector('.icon-btn[title="Anterior"]');
  const btnProximo  = document.querySelector('.icon-btn[title="Próximo"]');
  if (btnAnterior) btnAnterior.disabled = paginaAtual === 0;
  if (btnProximo)  btnProximo.disabled  = !temMaisEmails;
}

/* ── SINCRONIZA EM SEGUNDO PLANO ── */
async function sincronizarEmSegundoPlano() {
  try {
    const res = await apiFetch('/api/caixa/sincronizar', { method: 'POST' });
    if (!res) return;
    if (res.status === 401) {
      const data = await res.json().catch(() => ({}));
      if (data.erro === 'TOKEN_EXPIRADO') {
        toast('Sessão do Gmail expirada. Faça login novamente.', 'warning');
        setTimeout(() => { localStorage.clear(); window.location.href = 'login.html'; }, 2500);
        return;
      }
    }
    if (!res.ok) return;
    const data = await res.json();
    if (data.sincronizados > 0) {
      toast(`${data.sincronizados} novo(s) email(s)`, 'info');
      await carregarEmails(paginaAtual);
      atualizarBadgesClassificacao();
    }
  } catch (err) {
    console.error('Sync em segundo plano falhou:', err);
  }
}

async function carregarEmailsComRetry(tentativas = 3) {
  for (let i = 0; i < tentativas; i++) {
    try {
      await carregarEmails(0);
      return;
    } catch (err) {
      if (i < tentativas - 1) {
        toast(`Reconectando... (${i + 1}/${tentativas})`, 'info');
        await new Promise(r => setTimeout(r, 3000));
      }
    }
  }
}

function setLoadingState(on) {
  const btn = document.getElementById('refreshBtn');
  const svg = btn?.querySelector('svg');
  if (!svg) return;
  if (on) {
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

/* ── BADGES DE CLASSIFICAÇÃO NA SIDEBAR ── */
async function atualizarBadgesClassificacao() {
  try {
    const [rF, rS, rSeg] = await Promise.all([
      apiFetch('/api/caixa/classificacao/FRAUDE?pagina=0'),
      apiFetch('/api/caixa/classificacao/SUSPEITO?pagina=0'),
      apiFetch('/api/caixa/classificacao/SEGURO?pagina=0'),
    ]);
    const set = async (res, sel) => {
      if (!res || !res.ok) return;
      const d = await res.json();
      const b = document.querySelector(sel);
      if (b) { b.textContent = d.total || ''; b.style.display = d.total ? '' : 'none'; }
    };
    await set(rF,   '.nav-badge-fraude');
    await set(rS,   '.nav-badge-suspeito');
    await set(rSeg, '.nav-badge-seguro');
  } catch (err) { console.error(err); }
}

/* ── AVATAR DO USUÁRIO LOGADO ── */
function inicializarUsuario() {
  const nome  = localStorage.getItem('phishguard_nome') || '';
  const email = localStorage.getItem('phishguard_email') || '';
  const av = document.querySelector('.avatar');
  if (av) {
    const ini = nome ? nome.split(' ').slice(0,2).map(w=>w[0]).join('').toUpperCase() : email.substring(0,2).toUpperCase();
    av.textContent = ini;
    av.title = nome || email;
  }
  const dNome  = document.getElementById('dropdownNome');
  const dEmail = document.getElementById('dropdownEmail');
  if (dNome)  dNome.textContent  = nome || 'Usuário';
  if (dEmail) dEmail.textContent = email;
  const sInfo = document.getElementById('settingsAccountInfo');
  if (sInfo) sInfo.textContent = `Logado como: ${email}`;
  const avatarBtn = document.getElementById('avatarBtn');
  const dropdown  = document.getElementById('accountDropdown');
  if (avatarBtn && dropdown) {
    avatarBtn.addEventListener('click', e => { e.stopPropagation(); dropdown.style.display = dropdown.style.display === 'none' ? 'block' : 'none'; });
    document.addEventListener('click', () => { if (dropdown) dropdown.style.display = 'none'; });
  }
  function trocarConta() { localStorage.clear(); window.location.href = 'login.html'; }
  function sair() { if (confirm(`Sair da conta ${email}?`)) { localStorage.clear(); window.location.href = 'login.html'; } }
  document.getElementById('trocarContaBtn')?.addEventListener('click', trocarConta);
  document.getElementById('sairBtn')?.addEventListener('click', sair);
  document.getElementById('settingsTrocarConta')?.addEventListener('click', trocarConta);
  document.getElementById('settingsSair')?.addEventListener('click', sair);
}

/* ── DERIVED ── */
function getVisible() {
  let list = allEmails.filter(e => {
    if (currentFolder === 'starred') return e.starred;
    return e.folder === currentFolder;
  });
  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    list = list.filter(e => e.from.toLowerCase().includes(q) || e.subject.toLowerCase().includes(q) || e.preview.toLowerCase().includes(q));
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
    if (f === 'inbox') return;
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
    const phishBadge = badgePhishingLista(email.classificacao);
    row.innerHTML = `
      <div class="row-check"><div class="checkbox${isSel?' checked':''}" data-check="${email.id}">${isSel?'<svg width="12" height="12" fill="none" stroke="white" stroke-width="3" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>':''}</div></div>
      <div class="row-star"><button class="star-btn${email.starred?' starred':''}" data-star="${email.id}"><svg width="16" height="16" fill="${email.starred?'currentColor':'none'}" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg></button></div>
      <div class="row-sender">${email.from}</div>
      <div class="row-body">
        <span class="row-subject">${email.subject}${phishBadge}</span>
        <span class="row-preview">— ${email.preview}</span>
      </div>
      <div class="row-meta">
        ${email.unread ? '<div class="unread-dot"></div>' : ''}
        <span class="row-date">${email.date}</span>
        <div class="row-actions">
          <button class="row-action-btn" data-action="archive" data-id="${email.id}" title="Arquivar"><svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg></button>
          <button class="row-action-btn" data-action="delete" data-id="${email.id}" title="Excluir"><svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg></button>
          <button class="row-action-btn" data-action="markread" data-id="${email.id}" title="${email.unread?'Marcar como lido':'Marcar como não lido'}"><svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg></button>
        </div>
      </div>`;
    row.addEventListener('click', e => { if (e.target.closest('[data-check]')||e.target.closest('[data-star]')||e.target.closest('[data-action]')) return; openEmail(email); });
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
function toggleSelect(id) { selectedIds.has(id) ? selectedIds.delete(id) : selectedIds.add(id); renderEmails(); updateSelectAll(); }
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
async function toggleStar(id) {
  const email = allEmails.find(e => e.id === id);
  if (!email) return;
  email.starred = !email.starred;
  renderEmails(); refreshBadges();
  await apiFetch(`/api/caixa/${id}/favorito`, { method: 'PATCH', body: JSON.stringify({ favorito: email.starred }) });
}

/* ── READ ── */
function toggleRead(id) {
  const email = allEmails.find(e=>e.id===id);
  if (!email) return;
  const era = email.unread;
  email.unread = !email.unread;
  if (era) totalNaoLidos = Math.max(0, totalNaoLidos - 1);
  else     totalNaoLidos++;
  renderEmails(); refreshBadges(); atualizarContador();
  toast(email.unread ? 'Marcado como não lido' : 'Marcado como lido');
}
function markAsRead(id) {
  const email = allEmails.find(e=>e.id===id);
  if (email && email.unread) {
    email.unread = false;
    totalNaoLidos = Math.max(0, totalNaoLidos - 1);
    atualizarContador();
    apiFetch(`/api/caixa/${id}/lido`, { method: 'PATCH' });
  }
}

/* ── DELETE ── */
function deleteEmail(id) {
  const email = allEmails.find(e=>e.id===id);
  const idx   = allEmails.findIndex(e=>e.id===id);
  if (idx===-1) return;
  if (email?.unread) totalNaoLidos = Math.max(0, totalNaoLidos - 1);
  if (email?.folder === 'inbox') totalEmails = Math.max(0, totalEmails - 1);
  allEmails.splice(idx,1);
  selectedIds.delete(id);
  if (openEmailId===id) closeViewer();
  renderEmails(); refreshBadges(); atualizarContador();
  apiFetch(`/api/caixa/${id}`, { method: 'DELETE' });
  toast('Mensagem excluída','danger');
}
function deleteSelected() {
  if (!selectedIds.size) return;
  const count = selectedIds.size;
  [...selectedIds].forEach(id => {
    const email = allEmails.find(e=>e.id===id);
    const idx   = allEmails.findIndex(e=>e.id===id);
    if (idx!==-1) {
      if (email?.unread) totalNaoLidos = Math.max(0, totalNaoLidos - 1);
      if (email?.folder === 'inbox') totalEmails = Math.max(0, totalEmails - 1);
      allEmails.splice(idx,1);
    }
    if (openEmailId===id) closeViewer();
    apiFetch(`/api/caixa/${id}`, { method: 'DELETE' });
  });
  selectedIds.clear();
  renderEmails(); refreshBadges(); atualizarContador();
  toast(`${count} mensagem(s) excluída(s)`,'danger');
}

/* ── ARCHIVE ── */
function archiveEmail(id) {
  const email = allEmails.find(e=>e.id===id);
  if (!email) return;
  if (email.unread) totalNaoLidos = Math.max(0, totalNaoLidos - 1);
  if (email.folder === 'inbox') totalEmails = Math.max(0, totalEmails - 1);
  email.folder = 'archive';
  selectedIds.delete(id);
  if (openEmailId===id) closeViewer();
  renderEmails(); refreshBadges(); atualizarContador();
  apiFetch(`/api/caixa/${id}/pasta`, { method: 'PATCH', body: JSON.stringify({ pasta: 'archive' }) });
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
  const bodyEl   = document.getElementById('viewerBody');
  const isHtml   = email.body.trim().startsWith('<');
  const bodyHtml = isHtml ? email.body : email.body.split('\n').map(l => l.trim() ? `<p>${l}</p>` : '<p>&nbsp;</p>').join('');
  let phishPanel = '';
  if (email.classificacao) {
    const cfg = {
      SEGURO:   { bg:'#e6f4ea', border:'#34a853', color:'#1e8e3e', title:'Email seguro' },
      SUSPEITO: { bg:'#fff3e0', border:'#e65100', color:'#e65100', title:'Email suspeito' },
      FRAUDE:   { bg:'#fce8e6', border:'#c5221f', color:'#c5221f', title:'Possível fraude!' },
    }[email.classificacao] || {};
    const motivosHtml = email.motivos.length
      ? email.motivos.map(m=>`<li style="margin:4px 0;font-size:13px;">${m}</li>`).join('')
      : '<li style="font-size:13px;color:var(--text-muted)">Sem indicadores detectados</li>';
    phishPanel = `<div style="background:${cfg.bg};border-left:4px solid ${cfg.border};border-radius:8px;padding:14px 16px;margin-bottom:20px;"><div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;"><strong style="color:${cfg.color};font-size:14px;">${cfg.title}</strong><span style="color:${cfg.color};font-size:13px;opacity:0.8;">Score: ${email.score}/100</span></div><ul style="padding-left:18px;margin:0;color:var(--text-secondary);">${motivosHtml}</ul></div>`;
  }
  bodyEl.innerHTML = phishPanel + bodyHtml;
  const av = document.getElementById('viewerAvatar');
  av.textContent = initials(email.from);
  av.style.background = strColor(email.from);
  const svg = document.querySelector('#emailViewer .icon-btn[title="Com estrela"] svg');
  if (svg) svg.setAttribute('fill', email.starred ? 'currentColor' : 'none');
  document.getElementById('emailViewer').classList.add('open');
  renderEmails();
}
function closeViewer() {
  document.getElementById('emailViewer').classList.remove('open');
  openEmailId = null; renderEmails();
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
    document.getElementById('composeTo').value=''; document.getElementById('composeSubject').value=''; document.getElementById('composeBody').value=''; document.getElementById('composeTo').focus();
  }
}
document.getElementById('composeBtn').addEventListener('click', ()=>openCompose('new'));
document.getElementById('replyBtn').addEventListener('click',   ()=>openCompose('reply'));
document.getElementById('forwardBtn').addEventListener('click', ()=>openCompose('forward'));
document.getElementById('closeCompose').addEventListener('click', ()=>{ document.getElementById('composeModal').classList.remove('open'); composeMin=false; });
document.getElementById('minimizeCompose').addEventListener('click', ()=>{ composeMin=!composeMin; document.getElementById('composeModal').style.height=composeMin?'44px':''; });

document.getElementById('sendBtn').addEventListener('click', async () => {
  const to   = document.getElementById('composeTo').value.trim();
  const sub  = document.getElementById('composeSubject').value.trim();
  const body = document.getElementById('composeBody').value.trim();
  if (!to)   { toast('Informe o destinatário','warning'); return; }
  if (!sub)  { toast('Informe o assunto','warning'); return; }
  if (!body) { toast('O corpo está vazio','warning'); return; }
  const res = await apiFetch('/api/emails/enviar', { method:'POST', body: JSON.stringify({ para:to, assunto:sub, corpo:body }) });
  if (!res) return;
  if (res.ok) {
    const newId = Math.max(0, ...allEmails.map(e=>e.id))+1;
    allEmails.push({ id:newId, folder:'sent', from:'Você', addr:to, subject:sub, preview:body.substring(0,80), body, date:formatarData(new Date().toISOString()), unread:false, starred:false });
    document.getElementById('composeModal').classList.remove('open');
    toast('Mensagem enviada!','success');
    if (currentFolder==='sent') renderEmails();
    refreshBadges();
  } else {
    const err = await res.json().catch(()=>({}));
    toast('Erro ao enviar: '+(err.erro||'Tente novamente'),'danger');
  }
});
document.getElementById('discardBtn').addEventListener('click', ()=>{ document.getElementById('composeModal').classList.remove('open'); toast('Rascunho descartado','danger'); });

/* ── REFRESH ── */
document.getElementById('refreshBtn').addEventListener('click', async () => {
  await carregarEmails(paginaAtual);
  sincronizarEmSegundoPlano();
});

/* ── PAGINAÇÃO ── */
document.querySelector('.icon-btn[title="Anterior"]')?.addEventListener('click', () => {
  if (paginaAtual > 0) carregarEmails(paginaAtual - 1);
});
document.querySelector('.icon-btn[title="Próximo"]')?.addEventListener('click', () => {
  if (temMaisEmails) carregarEmails(paginaAtual + 1);
});

/* ── TOOLBAR BULK ── */
document.querySelectorAll('.tb-btn').forEach(btn=>{
  const t = btn.textContent.trim();
  if(t==='Excluir') btn.addEventListener('click', deleteSelected);
});

/* ── FOLDERS ── */
document.querySelectorAll('.nav-item[data-folder]').forEach(item=>{
  item.addEventListener('click', ()=>{
    document.querySelector('.nav-item.active')?.classList.remove('active');
    item.classList.add('active');
    currentFolder = item.dataset.folder;
    currentFilter = null;
    if (window.innerWidth <= 768) document.getElementById('sidebar')?.classList.remove('open');
    if (currentFolder === 'inbox') { selectedIds.clear(); closeViewer(); carregarEmails(0); return; }
    apiFetch(`/api/caixa/pasta/${currentFolder}?pagina=0`).then(async res => {
      if (!res || !res.ok) return;
      const data   = await res.json();
      const emails = data.emails || data;
      const outros = allEmails.filter(e => e.folder !== currentFolder);
      const novos  = emails.map(e => emailDoServidor(e, currentFolder));
      allEmails = [...outros, ...novos];
      renderEmails(); refreshBadges();
    });
  });
});

/* ── FILTROS DE CLASSIFICAÇÃO ── */
document.querySelectorAll('.nav-item[data-filter]').forEach(item => {
  item.addEventListener('click', () => {
    document.querySelector('.nav-item.active')?.classList.remove('active');
    item.classList.add('active');
    currentFilter = item.dataset.filter;
    selectedIds.clear(); closeViewer();
    if (window.innerWidth <= 768) document.getElementById('sidebar')?.classList.remove('open');
    apiFetch(`/api/caixa/classificacao/${currentFilter}?pagina=0`).then(async res => {
      if (!res || !res.ok) return;
      const data   = await res.json();
      const emails = data.emails || [];
      let novos    = emails.map(e => emailDoServidor(e, 'inbox'));
      novos.sort((a, b) => new Date(b.dateRaw||0) - new Date(a.dateRaw||0));
      paginaAtual   = data.pagina  ?? 0;
      temMaisEmails = data.temMais ?? false;
      totalEmails   = data.total   ?? novos.length;
      allEmails = [...novos, ...allEmails.filter(e => e.folder !== 'inbox')];
      renderEmails(); atualizarBotoesPagina();
      const pageInfo = document.getElementById('pageInfo');
      if (pageInfo) pageInfo.textContent = `${novos.length} de ${totalEmails}`;
    });
  });
});

/* ── SEARCH ── */
let searchTimer;
document.querySelector('.search-input').addEventListener('input', e=>{
  clearTimeout(searchTimer);
  searchTimer = setTimeout(()=>{ searchQuery=e.target.value.trim(); renderEmails(); }, 220);
});
document.querySelector('.search-input').addEventListener('keydown', e=>{
  if(e.key==='Escape'){ e.target.value=''; searchQuery=''; renderEmails(); e.target.blur(); }
});

/* ── KEYBOARD ── */
document.addEventListener('keydown', e => {
  const tag = document.activeElement.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA') return;
  if ((e.key==='e'||e.key==='E') && e.ctrlKey) { e.preventDefault(); openCompose('new'); }
  if (e.key==='Escape' && openEmailId) closeViewer();
  if ((e.key==='Delete'||e.key==='Backspace') && openEmailId) deleteEmail(openEmailId);
});

/* ── SETTINGS ── */
function openSettings() { syncSettingsUI(); document.getElementById('settingsPanel').classList.add('open'); document.getElementById('settingsOverlay').classList.add('open'); }
function closeSettings() { document.getElementById('settingsPanel').classList.remove('open'); document.getElementById('settingsOverlay').classList.remove('open'); }
document.getElementById('settingsBtn').addEventListener('click', openSettings);
document.getElementById('closeSettings').addEventListener('click', closeSettings);
document.getElementById('settingsOverlay').addEventListener('click', closeSettings);

function applySettings() {
  document.body.classList.toggle('dark', settings.dark);
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
  document.getElementById('darkToggle').checked = settings.dark;
  document.querySelectorAll('.density-opt').forEach(el=>el.classList.toggle('active',el.dataset.density===settings.density));
}
document.getElementById('darkToggle').addEventListener('change',e=>{ settings.dark=e.target.checked; applySettings(); });
document.querySelectorAll('.density-opt').forEach(el=>{ el.addEventListener('click',()=>{ settings.density=el.dataset.density; document.querySelectorAll('.density-opt').forEach(x=>x.classList.remove('active')); el.classList.add('active'); renderEmails(); }); });
document.getElementById('saveSettings').addEventListener('click',()=>{ applySettings(); closeSettings(); toast('Configurações salvas!','success'); });
document.getElementById('resetSettings').addEventListener('click',()=>{ settings={dark:false,fontSize:14,density:'default',accent:'#1a73e8'}; syncSettingsUI(); applySettings(); toast('Configurações restauradas'); });

/* ── HELP ── */
function openHelp() { document.getElementById('helpPanel').classList.add('open'); document.getElementById('helpOverlay').classList.add('open'); }
function closeHelp() { document.getElementById('helpPanel').classList.remove('open'); document.getElementById('helpOverlay').classList.remove('open'); }
document.getElementById('closeHelp').addEventListener('click', closeHelp);
document.getElementById('closeHelpBtn').addEventListener('click', closeHelp);
document.getElementById('helpOverlay').addEventListener('click', closeHelp);
document.getElementById('helpNavBtn')?.addEventListener('click', openHelp);

/* ── SIDEBAR MOBILE ── */
const sidebarEl  = document.getElementById('sidebar');
const sidebarOvl = document.getElementById('sidebarOverlay');
const menuBtn    = document.getElementById('menuBtn');
menuBtn?.addEventListener('click', () => sidebarEl?.classList.toggle('open'));
sidebarOvl?.addEventListener('click', () => sidebarEl?.classList.remove('open'));
function checkMobile() { if (menuBtn) menuBtn.style.display = window.innerWidth <= 768 ? 'flex' : 'none'; }
checkMobile();
window.addEventListener('resize', checkMobile);

/* ── INIT ── */
applySettings();
inicializarUsuario();
allEmails = [];
renderEmails();

carregarEmailsComRetry().then(() => {
  setTimeout(() => sincronizarEmSegundoPlano(), 1000);
  setTimeout(() => atualizarBadgesClassificacao(), 1500);
});