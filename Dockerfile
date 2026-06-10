FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y python3 python3-pip ffmpeg curl && \
    pip3 install "yt-dlp[default,curl-cffi]" && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Note the path change: Server/YoutubeSummarizer/target/
COPY app.jar app.jar

RUN ln -s /usr/local/bin/yt-dlp /app/yt-dlp

EXPOSE 8533

ENTRYPOINT ["java", "-jar", "app.jar"]