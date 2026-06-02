# 🤖 Amazon Promo Bot

Bot de promoções da Amazon com IA. Monitora ofertas automaticamente e publica no **WhatsApp** e **Telegram** com textos gerados pelo ChatGPT — incluindo link de afiliado.

---

## 🧪 Testando sem APIs reais (Modo Mock)

Você pode rodar e testar **tudo** sem ter nenhuma chave de API. O modo mock:
- Usa banco **H2 em memória** (sem precisar de PostgreSQL)
- Gera **produtos falsos** no lugar da Amazon API
- Gera **textos de venda pré-prontos** no lugar da OpenAI
- **Imprime no console** o que seria enviado ao Telegram e WhatsApp

### Rodando no modo mock (só precisa do Java 17)

```bash
# Clone o projeto
git clone https://github.com/seu-usuario/amazon-promo-bot.git
cd amazon-promo-bot

# Rode com o perfil mock
mvn spring-boot:run -Dspring-boot.run.profiles=mock
```

Você verá no console mensagens como:

```
╔══════════════════════════════════════════════════════╗
║        [MOCK TELEGRAM] Mensagem que seria enviada    ║
╠══════════════════════════════════════════════════════╣
║  🔥 OFERTA RELÂMPAGO! Preço nunca visto antes!
║
║  Esse produto finalmente entrou em promoção...
║  💰 De: R$ 349,00  →  Por: R$ 179,00
║  📉 49% OFF  |  Economia: R$ 170,00
╚══════════════════════════════════════════════════════╝
```

### Disparando manualmente (sem esperar o scheduler)

```bash
curl -X POST http://localhost:8081/api/executar
```

### Verificando o banco H2 pelo navegador

Acesse `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- User: `sa` / Senha: *(vazia)*

### Rodando os testes automatizados

```bash
mvn test
```

Os 4 testes cobrem: produto novo, duplicidade, ciclo completo e registro de canais.

---

## Fluxo de funcionamento

```
[Scheduler - a cada 1h]
        ↓
[Amazon PA API] → busca produtos com desconto por categoria
        ↓
[PostgreSQL] → verifica se o ASIN já foi enviado (evita duplicidade)
        ↓
[OpenAI GPT] → gera headline chamativa + texto de venda
        ↓
[Telegram Bot]  ←──── envia imagem + mensagem formatada
[WhatsApp (Evolution API)] ←── envia para grupos configurados
        ↓
[PostgreSQL] → salva o produto como "enviado"
```

---

## Pré-requisitos

| Ferramenta | Versão mínima |
|---|---|
| Java (JDK) | 17 |
| Maven | 3.8+ |
| Docker & Docker Compose | 24+ |
| VPS (produção) | 1 vCPU / 1 GB RAM |

---

## Configuração das APIs

### 1. Amazon Associates (PA API v5)
1. Cadastre-se em [affiliate-program.amazon.com.br](https://affiliate-program.amazon.com.br)
2. Crie suas credenciais em **Ferramentas → Product Advertising API**
3. Copie `Access Key`, `Secret Key` e sua `tag de afiliado` (ex: `seutag-20`)
> ⚠️ A conta precisa ter ao menos **3 vendas qualificadas** nos primeiros 180 dias para manter o acesso à API.

### 2. OpenAI
1. Acesse [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. Crie uma chave e copie para o `.env`

### 3. Telegram
1. Fale com [@BotFather](https://t.me/BotFather) → `/newbot`
2. Anote o **token** gerado
3. Crie um canal/grupo e adicione seu bot como **administrador**
4. Use `@nomecanal` ou o ID numérico como `TELEGRAM_CHAT_ID`

### 4. WhatsApp (Evolution API)
A Evolution API já sobe junto via Docker Compose.
Após subir, acesse `http://SEU-IP:8080` e:
1. Crie uma instância
2. Escaneie o QR Code com seu WhatsApp
3. Pegue os IDs dos grupos:
```
GET http://localhost:8080/group/fetchAllGroups/{instance}?getParticipants=false
```

---

## Como rodar

### 1. Clone e configure
```bash
git clone https://github.com/seu-usuario/amazon-promo-bot.git
cd amazon-promo-bot

cp .env.example .env
# Edite o .env com seus valores reais
nano .env
```

### 2. Suba tudo com Docker
```bash
docker-compose up -d --build
```

Isso vai subir:
- **PostgreSQL** na porta `5432`
- **Evolution API** na porta `8080`
- **Bot** na porta `8081`

### 3. Verifique se está rodando
```bash
# Health check
curl http://localhost:8081/api/health

# Logs do bot
docker-compose logs -f bot
```

### 4. Disparo manual (para testar)
```bash
curl -X POST http://localhost:8081/api/executar
```

---

## Desenvolvimento local (sem Docker)

```bash
# 1. Suba só o banco e a Evolution API
docker-compose up -d postgres evolution-api

# 2. Rode o bot direto pelo Maven
mvn spring-boot:run
```

---

## Endpoints da API

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/api/health` | Status do bot + total de produtos enviados |
| POST | `/api/executar` | Dispara o ciclo manualmente |
| GET | `/api/enviados?horas=24` | Lista produtos enviados nas últimas N horas |

---

## Configurações do Scheduler

Edite `scheduler.cron` no `application.yml` ou via variável de ambiente:

```yaml
scheduler:
  cron: "0 0 * * * *"   # a cada 1 hora
  max-produtos: 5        # máximo de promoções por ciclo
```

Exemplos de cron:
```
"0 0 * * * *"        → a cada 1 hora
"0 0/30 * * * *"     → a cada 30 minutos
"0 0 9,12,18 * * *"  → às 9h, 12h e 18h
```

---

## Categorias disponíveis (Amazon BR)

```
Eletronicos, Informatica, CasaECozinha, ModaFeminina,
ModaMasculina, Esportes, Brinquedos, Livros,
Automotivo, Saude, Beleza, Games
```

Configure em `amazon.categorias` no `application.yml`.

---

## Estrutura do projeto

```
amazon-promo-bot/
├── src/main/java/com/promocoes/bot/
│   ├── AmazonPromoBotApplication.java   # Entry point
│   ├── client/
│   │   ├── AmazonApiClient.java         # PA API v5 com AWS Signature v4
│   │   ├── OpenAiClient.java            # Geração de copy via ChatGPT
│   │   ├── TelegramBotClient.java       # Envio para Telegram
│   │   └── WhatsAppEvolutionClient.java # Envio para WhatsApp
│   ├── config/
│   │   └── BotController.java           # Endpoints REST utilitários
│   ├── dto/
│   │   ├── ProdutoDTO.java
│   │   └── CopyPromoDTO.java
│   ├── model/
│   │   └── ProdutoEnviado.java          # Entidade JPA
│   ├── repository/
│   │   └── ProdutoEnviadoRepository.java
│   ├── scheduler/
│   │   └── PromoScheduler.java          # Cron job
│   └── service/
│       └── PromoService.java            # Orquestração do fluxo
├── src/main/resources/
│   └── application.yml
├── docker-compose.yml
├── Dockerfile
├── .env.example
└── pom.xml
```

---

## Deploy em VPS

```bash
# Na sua VPS (Ubuntu/Debian)
sudo apt update && sudo apt install -y docker.io docker-compose git

git clone https://github.com/seu-usuario/amazon-promo-bot.git
cd amazon-promo-bot

cp .env.example .env && nano .env   # preencha as variáveis

docker-compose up -d --build

# Ver logs
docker-compose logs -f bot
```

> Recomendado: **Oracle Cloud Free Tier** (gratuito para sempre) ou **Contabo VPS** (~$5/mês).
