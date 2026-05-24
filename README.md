# PhishGuard API

API REST desenvolvida com Spring Boot para análise inteligente de emails com foco em detecção de phishing, fraudes e links maliciosos.

A aplicação funciona como um firewall inteligente de emails, utilizando:

- Heurísticas próprias de detecção
- Integração com Safe Browsing
- Inteligência Artificial via Groq
- Bases OpenPhish e URLhaus
- Login com Gmail OAuth2
- Segurança com JWT
- Banco de dados para persistência de usuários e URLs maliciosas

---

# Funcionalidades

## Classificação Inteligente de Emails

Os emails são classificados em:

| Classificação | Descrição |
|---|---|
| Seguro | Email legítimo |
| Suspeito | Possíveis sinais de phishing |
| Fraude | Alto risco ou confirmação maliciosa |

---

## Heurística Base

A API realiza análises como:

- Domínio suspeito
- Links encurtados
- Linguagem de urgência
- HTML suspeito
- Spoofing de remetente
- Divergência entre domínio e remetente
- Palavras relacionadas a golpes
- Links ocultos

---

## Verificação de Links

Todos os links encontrados nos emails passam por verificações utilizando:

- Google Safe Browsing
- Base local OpenPhish
- Base local URLhaus

---

## Inteligência Artificial

Emails classificados inicialmente como suspeitos passam por uma segunda camada de análise utilizando IA via Groq.

A IA analisa:

- Engenharia social
- Contexto da mensagem
- Linguagem manipulativa
- Tentativas de urgência
- Padrões modernos de phishing

---

## Segurança

- Login OAuth2 com Gmail
- Autenticação JWT
- Proteção de rotas
- Acesso somente aos próprios emails
- Persistência segura dos dados

---

# Arquitetura

```text
Usuário → Login Gmail OAuth2
        → Recebe JWT
        → Requisição autenticada
        → Captura emails
        → Heurística analisa
        → Verificação de links:
             • Safe Browsing
             • OpenPhish
             • URLhaus
        → IA Groq analisa emails suspeitos
        → Classificação final:
             • Seguro
             • Suspeito
             • Fraude
```

---

# Tecnologias Utilizadas

## Backend

- Java
- Spring Boot
- Spring Security
- JWT
- OAuth2

## Banco de Dados

- PostgreSQL / MySQL

## APIs e Integrações

- Gmail API
- Safe Browsing API
- Groq API
- OpenPhish
- URLhaus

---

# Autenticação

A autenticação é feita utilizando OAuth2 com Gmail.

Após o login:

1. O usuário autentica com Google
2. A API gera um token JWT
3. O JWT é utilizado nas próximas requisições

---

## Header de Autorização

```http
Authorization: Bearer SEU_TOKEN_JWT
```

---

# Endpoints

## Analisar Emails

### Endpoint

```http
GET /api/analisar
```

### Descrição

Captura e analisa os emails do usuário autenticado.

---

### Exemplo de Resposta

```json
[
  {
    "remetente": "suporte@paypal-alerta.com",
    "assunto": "Sua conta será bloqueada",
    "classificacao": "FRAUDE",
    "motivos": [
      "Domínio suspeito",
      "Link malicioso detectado",
      "Linguagem de urgência"
    ]
  },
  {
    "remetente": "github.com",
    "assunto": "Novo login detectado",
    "classificacao": "SEGURO"
  }
]
```

---

## Atualizar Base de URLs

### Endpoint

```http
POST /urlhaus
```

### Descrição

Atualiza as bases OpenPhish e URLhaus e salva os dados no banco.

---

### Exemplo de Resposta

```json
{
  "status": "success",
  "urlsSalvas": 14520
}
```

---

# Estrutura do Banco de Dados

## Tabela: usuarios

| Campo | Tipo |
|---|---|
| id | Long |
| nome | String |
| email | String |
| google_id | String |
| created_at | Timestamp |

---

## Tabela: url_phishing

| Campo | Tipo |
|---|---|
| id | Long |
| url | String |
| dominio | String |
| source | String |
| created_at | Timestamp |

---

# Sistema de Feed de URLs

A aplicação utiliza duas fontes externas para alimentar automaticamente a base de URLs maliciosas:

## OpenPhish

O sistema consome automaticamente o feed:

```text
https://openphish.com/feed.txt
```

As URLs são:

- Baixadas automaticamente
- Filtradas
- Normalizadas
- Persistidas no banco
- Validadas contra duplicações

---

## URLhaus

O sistema também realiza download do feed CSV compactado:

```text
https://urlhaus.abuse.ch/downloads/csv_recent/
```

Durante o processamento:

- O ZIP é baixado automaticamente
- O CSV é descompactado
- Apenas URLs online são salvas
- URLs duplicadas são ignoradas
- O domínio é extraído automaticamente
- O tipo de ameaça é armazenado

---

# Fluxo de Análise

```text
1. Usuário realiza login Google
2. JWT é gerado
3. Usuário chama /api/analisar
4. API captura emails Gmail
5. Links são extraídos
6. Verificação:
   • OpenPhish
   • URLhaus
   • Safe Browsing
7. Heurística realiza análise
8. IA Groq analisa emails suspeitos
9. Resultado final retornado
```

---

# Segurança Implementada

- JWT Authentication
- OAuth2 Google Login
- Proteção de endpoints
- Isolamento de dados por usuário
- Sanitização de URLs
- Verificação de ameaças externas

---

# Objetivo do Projeto

A PhishGuard API foi criada para fornecer uma camada inteligente de proteção contra phishing, engenharia social e links maliciosos, automatizando análises e reduzindo riscos de fraudes digitais.

---

# Autor

Desenvolvido por Rafael Farias.
