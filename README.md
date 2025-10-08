# Boomslime Bot

Discord music bot that downloads and plays Spotify tracks in Java/Maven using SpotDL

## Main

- plays Spotify tracks and playlists on Discord
- downloads songs as .mp3 and caches them locally for 6 months with 24h verify (configurable)
- keeps playlist order and avoids duplicate downloads
- supports queue, skip, stop, and history commands
- retries failed downloads automatically up to 3 times (configurable)
- if exists an music download error, notify user on the discord chat

## Tech stack

- Java 21
- JDA (Java Discord API) 5.2.1
- LavaPlayer 2.2.2 for audio playback
- Spotify Web API Java 8.4.1
- SpotDL for downloading tracks
- Maven for build management

## Available commands

- `!play <url>` or `!p <url>` - play a spotify track or playlist
- `!queue` or `!q` - show current queue with pagination
- `!skip` or `!s` - skip to next track
- `!rewind` or `!prev` or `!previous` - go back to previous track
- `!pause` - pause playback
- `!resume` or `!unpause` - resume playback
- `!stop` - stop playback, clear queue, and cancel all downloads
- `!nowplaying` or `!np` - show current track info with progress bar
- `!shuffle` or `!embaralhar` - shuffle the queue
- `!help` or `!ajuda` - show command list

## How to run

1. clone the repository:

```bash
git clone https://github.com/toomazs/boomslime-bot.git
cd boomslime-bot
```

2. create `.env` file in project root:

```env
TOKEN=your_discord_bot_token
SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret
MUSIC_DIR=./data/music
DATA_DIR=./data
```

3. install dependencies (spotdl, ytdlp, ffmpeg):

```bash
pip3 install spotdl

# ytdlp dep comes with the spotdl. if it doesnt come, access https://github.com/yt-dlp/yt-dlp/wiki/Installation
# ensure ffmpeg is installed on your system too.
```

4. build the project:

```bash
mvn clean package
```

5. run the bot:

```bash
java -jar target/boomslime-bot-1.0-SNAPSHOT.jar
```

### Running on cloud VMs (optional)

if you're running this on a cloud VM (DigitalOcean, AWS, Azure, etc.), you will probably have to use an SSH reverse tunnel proxy. <br>
this is a neccessary step because the yt-dlp block datacenters IPs, and block SOCKS5 proxies. configure that way converting to HTTP:

1. set up SSH reverse tunnel from your local machine to the VM
2. configure a SOCKS5 to HTTP proxy converter (e.g., privoxy)
3. add to `.env`:

```env
PROXY_SERVER=http://127.0.0.1:8118
```

this routes spotdl traffic through your residential IP, bypassing datacenter restrictions.

## Project structure

```
src/main/java/com/tomaz/boomslime/
├── BoomslimeBot.java              # entry point
├── commands/
│   └── CommandManager.java         # command handling and routing
├── config/
│   └── BotConfig.java             # environment variable management
├── music/
│   ├── AudioPlayerSendHandler.java # JDA audio bridge
│   ├── DownloadManager.java        # download orchestration with cancellation
│   ├── GuildMusicManager.java      # per-guild audio player instance
│   ├── PlayerManager.java          # track loading and queue management
│   ├── SpotifyDownloader.java      # spotdl wrapper with retry logic
│   └── TrackScheduler.java         # playback scheduling and fade-out
└── services/
    └── SpotifyService.java         # spotify API integration
```
