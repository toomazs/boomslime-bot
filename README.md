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
- spotdl (download de musicas via proxy SSH)
- yt-dlp (backend do spotdl)
- ffmpeg (processamento de audio)
- privoxy (converte SOCKS5 → HTTP para spotdl)
- túnel SSH (usa IP residencial em VMs/datacenters)
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
```env
TOKEN=seu_token_do_discord
SPOTIFY_CLIENT_ID=seu_client_id
SPOTIFY_CLIENT_SECRET=seu_client_secret
MUSIC_DIR=./data/music
DATA_DIR=./data
PROXY_SERVER=http://127.0.0.1:8118
```

### 2. Instala dependências

```bash
# Ubuntu/Debian
sudo apt install ffmpeg python3 python3-pip default-jdk maven
pip3 install spotdl yt-dlp

# Arch
sudo pacman -S ffmpeg python python-pip jdk-openjdk maven
pip install spotdl yt-dlp
```

### 3. Builda o projeto

```bash
mvn clean package
```

### 4. Roda o bot

```bash
java -jar target/boomslime-bot-1.0-SNAPSHOT.jar
```

**Nota:** Para rodar em VMs/datacenters onde YouTube bloqueia IPs, veja a seção [Deploy em servidores](#deploy-em-servidores-railwayvmscloud) abaixo.

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

### Solução para VMs/Datacenters (YouTube bloqueia IPs):

**Problema:** YouTube bloqueia downloads de IPs de datacenter (Azure, Oracle, DigitalOcean, etc.)

**Solução:** Use túnel SSH reverso para rotear tráfego pela sua máquina local (IP residencial)

#### Setup do túnel SSH (GRÁTIS):

**1. Na sua máquina local (deve ficar ligada):**

Instala SSH server:
```bash
sudo apt install openssh-server
sudo systemctl start ssh
sudo systemctl enable ssh
```

Cria túnel SOCKS5 reverso pro servidor:
```bash
# Substitua USER e SERVER-IP pelos dados do seu servidor
ssh -D 9050 -N -f USER@SERVER-IP

# Exemplo:
# ssh -D 9050 -N -f root@68.183.227.146
```

**2. No servidor, adiciona no `.env`:**
```bash
PROXY_SERVER=socks5://localhost:9050
COOKIE_RENEWER_ENABLED=true
```

**3. Copia cookies.txt da sua máquina pro servidor:**
```bash
# Gera cookies.txt no Firefox com extensão "Get cookies.txt LOCALLY"
# Acesse https://music.youtube.com/ e exporte os cookies

# Copia pro servidor
scp cookies.txt USER@SERVER:/caminho/bot/
```

**4. No servidor, cria sessão inicial:**
```bash
# Cria diretório de sessão
mkdir -p data/browser-session

# Opção A: Copia state.json da máquina local (se tiver)
scp -r data/browser-session USER@SERVER:/caminho/bot/data/

# Opção B: Gera sessão vazia (renovação vai criar depois)
echo '{"cookies":[],"origins":[]}' > data/browser-session/state.json
```

**5. Inicia o bot:**
```bash
java -jar target/boomslime-bot-1.0-SNAPSHOT.jar
```

O bot vai:
- ✅ Usar o proxy SOCKS5 (túnel SSH)
- ✅ YouTube vê seu IP residencial
- ✅ Renovar cookies automaticamente a cada 6h
- ✅ Funcionar perfeitamente em qualquer datacenter

**Custo:** R$ 0,00 (apenas sua máquina ligada)
**Consumo:** ~5-10 MB por música (~500 MB-1 GB/mês)

---

### Troubleshooting:

**Erro "Sign in to confirm you're not a bot":**
- Configure o túnel SSH (instruções acima)
- Certifique-se de que `PROXY_SERVER` está configurado no .env
- Verifique se cookies.txt existe e é válido

**Túnel SSH desconecta:**
```bash
# Use autossh para manter túnel sempre ativo
sudo apt install autossh
autossh -M 0 -D 9050 -N -f USER@SERVER
```

**Servidor sem interface gráfica (headless):**
- Use cookies.txt exportado do Firefox (método recomendado)
- Não é necessário rodar navegador no servidor

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
