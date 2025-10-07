# Estágio 1: Build com Maven (sem alteração)
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Estágio 2: Execução com todas as dependências
FROM eclipse-temurin:21-jdk-jammy

# Instala as dependências do sistema: Python, Pip e FFmpeg.
RUN apt-get update && \
    apt-get install -y python3 python3-pip ffmpeg && \
    apt-get clean

# Instala versões específicas e atualizadas do spotdl e yt-dlp
# yt-dlp precisa ser sempre a versão mais recente para funcionar
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir --upgrade yt-dlp && \
    pip3 install --no-cache-dir spotdl

WORKDIR /app

# Copia o JAR do estágio de build
COPY --from=build /app/target/boomslime-bot-1.0-SNAPSHOT.jar .

# Define o comando de início
CMD ["java", "-jar", "boomslime-bot-1.0-SNAPSHOT.jar"]