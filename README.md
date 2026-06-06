#  PhishGuard

Sistema inteligente de detecção de phishing em e-mails, desenvolvido com Java + Spring Boot. O PhishGuard conecta-se ao Gmail do usuário, analisa cada e-mail recebido com múltiplas camadas de verificação e classifica automaticamente como **SEGURO**, **SUSPEITO** ou **FRAUDE**.

🔗 **Deploy:** [https://a3-74um.onrender.com](https://a3-74um.onrender.com)

---

##  Funcionalidades

-  Login com conta Google (OAuth2 + JWT)
-  Sincronização automática com Gmail API (50 e-mails por ciclo)
-  Pipeline de análise com 7 camadas:
  - Whitelist de remetentes confiáveis (LinkedIn, Google, bancos, etc.)
  - Base interna de remetentes e domínios golpistas
  - Verificação de URLs contra **URLHaus** (CSV atualizado) e **OpenPhish**
  - Análise heurística local (domínios suspeitos, palavras-chave, links externos)
  - Extração e validação de todos os links do corpo do e-mail
  - Análise por **IA (Groq)**
  - Classificação final com score de 0 a 100
-  Badge de classificação visível em todos os e-mails da lista
-  Filtros por classificação na sidebar (Fraude, Suspeito, Seguro)
-  Paginação de 20 e-mails por página
-  Favoritos, pastas e marcação de lido/não lido
-  Modo escuro
-  Interface responsiva (mobile e desktop)
- Deploy em nuvem no Render

---

## Como funciona a análise

Cada e-mail passa pelo `PhishingOrchestrator`, que executa as etapas abaixo em sequência. O processo é interrompido assim que uma conclusão clara é encontrada:

```
1. Whitelist de confiáveis  →  se remetente for confiável: SEGURO
2. Base de golpistas        →  se remetente conhecido: FRAUDE direto
3. URLHaus / OpenPhish      →  se URL do e-mail estiver na base: FRAUDE
4. Heurística local         →  verifica domínio, palavras e padrões suspeitos
5. Extração de links        →  analisa todos os links do corpo HTML
6. Análise por IA (Groq)    →  LLaMA 3.3-70b avalia o e-mail completo
7. Score final              →  SEGURO (< 40) / SUSPEITO (40-79) / FRAUDE (>= 80)
```

### URLHaus

O URLHaus é uma plataforma mantida pela abuse.ch que disponibiliza um feed CSV com URLs maliciosas ativas reportadas pela comunidade. O PhishGuard baixa esse CSV compactado (`.zip`), filtra apenas as URLs com status `online`, extrai os domínios e armazena até 2.000 entradas no banco. Domínios genéricos como `.com.br`, `.net`, `.org` são ignorados para evitar falsos positivos.

### OpenPhish

O OpenPhish disponibiliza um feed com URLs de phishing conhecidas e verificadas. O PhishGuard consome esse feed e mantém a base atualizada para cruzar com os links encontrados nos e-mails analisados.

---

##  Tecnologias

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 17 + Spring Boot 3 |
| Segurança | Spring Security + OAuth2 + JWT |
| Banco de dados | PostgreSQL (Render) |
| E-mail | Gmail API v1 |
| IA | Groq API (LLaMA 3.3-70b) |
| URLs maliciosas | URLHaus CSV Feed + OpenPhish |
| Frontend | HTML + CSS + JavaScript (sem frameworks) |
| Deploy | Render (Web Service + PostgreSQL) |
| Build | Maven |

---

##  Como rodar localmente

### Pré-requisitos

- Java 17+
- Maven
- PostgreSQL
- Projeto configurado no Google Cloud (Gmail API + OAuth2 habilitados)
- Chave de API do Groq

### 1. Clone o repositório

```bash
git clone https://github.com/Tsukiii3/A3.git
cd A3
```

### 2. Configure as variáveis de ambiente

```env
GOOGLE_CLIENT_ID=seu_client_id
GOOGLE_CLIENT_SECRET=seu_client_secret
GOOGLE_REDIRECT_URI=http://localhost:8080/callback.html
GOOGLE_CREDENTIALS={"web":{"client_id":"...","client_secret":"...","redirect_uris":[...],...}}
GROQ_API_KEY=sua_chave_groq
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/phishguard
SPRING_DATASOURCE_USERNAME=seu_usuario
SPRING_DATASOURCE_PASSWORD=sua_senha
```

### 3. Rode a aplicação

```bash
mvn spring-boot:run
```

Acesse em: `http://localhost:8080`

---

## 📡 Endpoints da API

Todos os endpoints protegidos exigem o header:
```
Authorization: Bearer <token>
```

### Autenticação — `/auth`

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/auth/google` | Troca código OAuth2 por JWT da aplicação | Não |
| GET | `/auth/me` | Retorna dados do usuário autenticado | Sim |

### Caixa de E-mails — `/api/caixa`

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/api/caixa/sincronizar` | Busca novos e-mails do Gmail, analisa e salva |
| GET | `/api/caixa/pasta/{pasta}` | Lista e-mails por pasta com paginação |
| GET | `/api/caixa/classificacao/{class}` | Filtra por SEGURO / SUSPEITO / FRAUDE |
| PATCH | `/api/caixa/{id}/lido` | Marca e-mail como lido | Sim |
| PATCH | `/api/caixa/{id}/favorito` | Adiciona ou remove dos favoritos |
| PATCH | `/api/caixa/{id}/pasta` | Move e-mail para outra pasta |
| DELETE | `/api/caixa/{id}` | Remove e-mail do banco | 
| POST | `/api/caixa/reanalisar` | Reanálisa todos os e-mails sem classificação |

### Gmail — `/api/emails`

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/api/emails/analisar` | Analisa um e-mail avulso enviado no body |
| POST | `/api/emails/enviar` | Envia e-mail pelo Gmail do usuário |

### Administração — `/api/admin`

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/api/admin/stats` | Estatísticas gerais (totais por classificação) |
| GET | `/api/admin/popular-urls` | Carrega/atualiza URLs do URLHaus no banco | 
| POST | `/api/admin/recarregar-openphish` | Recarrega o feed do OpenPhish |
| DELETE | `/api/admin/limpar-dominio` | Remove domínio específico da base de phishing | 
| DELETE | `/api/admin/remover-remetente` | Remove remetente da base de golpistas | 

---

## 🗄️ Banco de Dados

```
usuarios              → usuários autenticados com tokens OAuth2
emails_salvos         → e-mails sincronizados com classificação, score e data original
email_salvo_motivos   → motivos detalhados de cada classificação
emails_golpistas      → remetentes e domínios identificados como maliciosos
email_motivos         → motivos associados a golpistas conhecidos
urls_phishing         → URLs maliciosas carregadas do URLHaus e OpenPhish
```

---

## 📁 Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/phishguard/demo/
│   │   ├── controller/      → AuthController, EmailController, GmailController, OpenPhishController
│   │   ├── model/           → Entidades JPA (EmailSalvo, Usuario, UrlPhishing...)
│   │   ├── repository/      → Repositórios Spring Data JPA
│   │   ├── service/         → GmailService, AiAnalyseService, PhishingService
│   │   ├── Orchestrator/    → PhishingOrchestrator (pipeline completo)
│   │   ├── loader/          → UrlHausLoader (carrega CSV do URLHaus)
│   │   ├── dto/             → GmailDTO, AnalyseDTO
│   │   └── security/        → JwtFilter, JwtService, SecurityConfig
│   └── resources/
│       └── static/          → Frontend (index.html, login.html, callback.html, script.js, meu.css)
```

---

## 👤 Autor

**Rafael da Silva Farias**  
Universidade Anhembi Morumbi — 2026
