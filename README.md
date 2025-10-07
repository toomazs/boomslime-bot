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

1. cria um arquivo `.env` na raiz do projeto com:
```
TOKEN=seu_token_do_discord
SPOTIFY_CLIENT_ID=seu_client_id
SPOTIFY_CLIENT_SECRET=seu_client_secret
MUSIC_DIR=./data/music
```

2. instala as dependencias:
```bash
# ubuntu/debian
sudo apt install ffmpeg python3 python3-pip
pip3 install spotdl yt-dlp

# arch
sudo pacman -S ffmpeg python python-pip
pip install spotdl yt-dlp
```

3. builda o projeto:
```bash
mvn clean package
```

4. roda o bot:
```bash
java -jar target/boomslime-bot-1.0-SNAPSHOT.jar
```

## deploy no railway

o projeto ja vem com `nixpacks.toml` configurado pra deploy no railway.

so precisa adicionar as variaveis de ambiente no painel do railway:
- `TOKEN`
- `SPOTIFY_CLIENT_ID`
- `SPOTIFY_CLIENT_SECRET`
- `MUSIC_DIR` (opcional, padrao: `./data/music`)

o railway tem 5gb de disco, suficiente pra ~1400 musicas em cache.

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
