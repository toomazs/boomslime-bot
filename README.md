# boomslime bot

bot de musica do discord que baixa e toca musicas do spotify

## o que esse bot faz

- toca musicas do spotify no discord
- baixa as musicas em mp3 e guarda em cache
- suporta playlists inteiras mantendo a ordem original
- tem fade-out nos ultimos 3 segundos de cada musica
- sistema de fila com paginacao
- comandos de controle basicos (play, skip, rewind, stop, etc)

## tecnologias usadas

- java 21
- jda (java discord api) 5.2.1
- lavaplayer 2.2.2 (audio player)
- spotify web api java 8.4.1
- playwright 1.48.0 (renovação automática de cookies)
- spotdl (download de musicas)
- yt-dlp (backend do spotdl)
- ffmpeg (processamento de audio)
- maven (build)

## comandos

- `!play <url>` - toca uma musica ou playlist do spotify
- `!skip` ou `!pular` - pula pra proxima musica
- `!rewind` ou `!voltar` - volta pra musica anterior
- `!stop` ou `!parar` - para tudo e limpa a fila
- `!queue` ou `!fila` - mostra as musicas na fila (com paginacao)
- `!shuffle` ou `!embaralhar` - embaralha a fila
- `!nowplaying` ou `!np` - mostra a musica tocando agora
- `!history` ou `!historico` - mostra as ultimas musicas tocadas

## como rodar

### 1. Configuração inicial

Cria um arquivo `.env` na raiz do projeto:
```
TOKEN=seu_token_do_discord
SPOTIFY_CLIENT_ID=seu_client_id
SPOTIFY_CLIENT_SECRET=seu_client_secret
MUSIC_DIR=./data/music
COOKIE_RENEWER_ENABLED=true
DATA_DIR=./data
```

### 2. Instala dependências

```bash
# Ubuntu/Debian
sudo apt install ffmpeg python3 python3-pip default-jdk maven
pip3 install spotdl yt-dlp playwright
playwright install firefox
playwright install-deps firefox

# Arch
sudo pacman -S ffmpeg python python-pip jdk-openjdk maven
pip install spotdl yt-dlp playwright
playwright install firefox
playwright install-deps firefox
```

### 3. Setup de cookies do YouTube (IMPORTANTE!)

O bot precisa de cookies válidos do YouTube para baixar músicas em servidores (VMs).

**Execute o setup inicial:**
```bash
mvn clean package
java -cp target/boomslime-bot-1.0-SNAPSHOT.jar com.tomaz.boomslime.utils.CookieRenewer
```

**O que vai acontecer:**
1. Um navegador Firefox vai abrir automaticamente
2. Faça login na sua conta do YouTube/Google (pode ser conta grátis)
3. Feche o navegador manualmente após o login
4. Os cookies serão salvos e renovados automaticamente a cada 6 horas

**IMPORTANTE:**
- Execute este comando **no servidor/VM** onde o bot vai rodar
- Você precisa de interface gráfica (X11) ou X11 forwarding habilitado
- Se estiver em servidor sem GUI, use: `ssh -X user@servidor` para conectar com X11 forwarding

### 4. Roda o bot

```bash
java -jar target/boomslime-bot-1.0-SNAPSHOT.jar
```

O bot vai:
- ✅ Iniciar o renovador automático de cookies (roda a cada 6h em background)
- ✅ Baixar músicas usando cookies válidos do YouTube
- ✅ Funcionar em qualquer datacenter (Azure, Oracle, DigitalOcean, etc.)

## deploy em servidores (Railway/VMs/Cloud)

O projeto já vem com `nixpacks.toml` configurado.

### Variáveis de ambiente necessárias:
- `TOKEN` - Token do bot Discord
- `SPOTIFY_CLIENT_ID` - Client ID do Spotify
- `SPOTIFY_CLIENT_SECRET` - Client Secret do Spotify
- `MUSIC_DIR` - Diretório de cache (padrão: `./data/music`)
- `COOKIE_RENEWER_ENABLED` - `true` para renovação automática (padrão: `true`)
- `DATA_DIR` - Diretório de dados (padrão: `./data`)

### Setup inicial em servidores:

**1. Faça deploy normalmente**

**2. Execute o setup de cookies via SSH com X11 forwarding:**
```bash
# Conecta com X11 forwarding
ssh -X user@seu-servidor

# Executa setup de cookies
java -cp boomslime-bot-1.0-SNAPSHOT.jar com.tomaz.boomslime.utils.CookieRenewer
```

**3. Reinicia o bot**

Os cookies serão renovados automaticamente a cada 6 horas em background.

### Troubleshooting:

**Erro "Sign in to confirm you're not a bot":**
- Execute o setup de cookies (passo 2 acima)
- Certifique-se de que `COOKIE_RENEWER_ENABLED=true` no .env

**Servidor sem interface gráfica (headless):**
- Instale Xvfb: `sudo apt install xvfb`
- Execute setup com Xvfb: `xvfb-run java -cp boomslime-bot.jar com.tomaz.boomslime.utils.CookieRenewer`
- Ou use VNC viewer para acessar GUI remotamente

## sistema de cache

- musicas baixadas ficam salvas em `./data/music/`
- cada arquivo tem o id do spotify no nome pra evitar duplicatas
- auto-limpeza roda a cada 6 horas removendo arquivos com mais de 24 horas
- se a musica ja foi baixada antes, usa o cache ao invez de baixar denovo

## sistema de retry

se o download falhar (ex: rate limit do youtube):
1. tenta ate 3 vezes com delay de 5 segundos entre tentativas
2. se falhar 3 vezes, pula a musica e avisa no discord
3. continua tocando as proximas da fila

## observacoes

- suporta urls do spotify em qualquer idioma (`/track/`, `/intl-pt/track/`, etc)
- playlists sao baixadas sequencialmente pra manter a ordem
- delay de 5 segundos entre downloads de playlist pra evitar rate limit
- historico mantem as ultimas 50 musicas tocadas
- mensagem "tocando agora" tem delay de 1.5s pra nao aparecer antes dos comandos
