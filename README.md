# PhishGuard — Email Phishing Detection API

Sistema de detecção de phishing desenvolvido com **Java + Spring Boot**, utilizando:

*  Heurística (regras técnicas)
* Google Safe Browsing
* Inteligência Artificial (camada final de decisão)

---

##  Objetivo

Detectar automaticamente emails suspeitos e classificá-los em:

* SEGURO
* SUSPEITO
*  FRAUDE

Retornando também:

* Score de risco (0–100)
* Motivos da classificação

---

##  Autenticação

O sistema utiliza autenticação com a API do Gmail via Google.

* A autenticação é feita via **OAuth2 do Google**
* O usuário precisa autorizar o acesso à sua conta Gmail
* O sistema utiliza um arquivo de credenciais (`credentials.json`)

* O próprio Google já gerencia:

  * autenticação
  * autorização
  * segurança da conta

---

## Arquitetura do Sistema

O projeto segue uma arquitetura em camadas:

<img width="946" height="377" alt="image" src="https://github.com/user-attachments/assets/2d182115-9b50-468f-ba96-f4ca9b304cae" />
<img width="953" height="174" alt="image" src="https://github.com/user-attachments/assets/061a216e-e909-4189-bb9b-4de77ea726bc" />


---

### Camadas do sistema

###  Controller

Responsável por expor os endpoints da API.

---

###  Orchestrator (Cérebro do sistema)

Gerencia o fluxo completo de análise:

1. Heurística
2. Safe Browsing
3. IA

 Exemplo:

```java
if (resultado.getScore() < 25) {
    return resultado; // evita processamento desnecessário
}
```

---

###  Services

#### PhishingService

Responsável pela análise heurística:

* Domínio do remetente
* Links
* Palavras suspeitas
* Score inicial

---

####  SafeBrowsingService

Integração com API do Google:

* Detecta URLs maliciosas
* Evita falsos positivos com whitelist

---

#### AiAnalyseService

Responsável pela análise com IA:

* Utilizado apenas em casos críticos
* Atua como decisão final

---

###  Repository (Camada de Dados)

Mesmo que não esteja sendo amplamente utilizado ainda, essa camada é importante porque:

Permite futura integração com banco de dados

Possíveis usos futuros:

* Armazenar links analisados
* Histórico de phishing
* 
---

##  Lógica de Detecção

### Heurística

Analisa:

* Remetente (email pessoal ou domínio desconhecido)
* Links externos
* Palavras-chave suspeitas:
* 
  * urgente
  * bloqueio
  * login
  * verify

---

###  Safe Browsing

Só é acionado quando necessário

##  Extração de Links

detectar URLs dentro do email:

##  Endpoint

```http
GET /api/emails/analisar
```

---

## Exemplo de Resposta

```json
{
  "from": "Google <no-reply@accounts.google.com>",
  "subject": "Alerta de segurança",
  "classificacao": "SEGURO",
  "score": 10,
  "motivos": [
    "Links externos desconhecidos"
  ]
}
```

---

##  Demonstração

###  Requisição (Postman)

<img width="1364" height="841" alt="image" src="https://github.com/user-attachments/assets/0e020f17-ab20-4e14-9035-d78adad0ba19" />
<img width="1333" height="482" alt="image" src="https://github.com/user-attachments/assets/fe509953-3cfd-464f-8675-d586000039c9" />



---


###  Estrutura do Projeto

<img width="366" height="838" alt="image" src="https://github.com/user-attachments/assets/f2b25845-cdb4-4786-8d75-a74cc516c312" />
<img width="356" height="490" alt="image" src="https://github.com/user-attachments/assets/65387b01-0c17-40f9-bf2c-48ed44fa964c" />


---

## Tecnologias

* Java 21
* Spring Boot
* Maven
* Google Safe Browsing API
* Gmail API (OAuth2)



