FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y python3 ffmpeg curl && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod +x /usr/local/bin/yt-dlp && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/YoutubeSummarizer-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8533

ENTRYPOINT ["java", "-jar", "app.jar"]