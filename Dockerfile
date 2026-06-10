FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y python3 ffmpeg curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Need Deno to make yt-dlp work properly since it
# needs js
RUN curl -fsSL https://deno.land/install.sh | sh
ENV PATH="/root/.deno/bin:${PATH}"

# Download yt-dlp
RUN curl -L \
    https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux \
    -o /app/yt-dlp && \
    chmod +x /app/yt-dlp

WORKDIR /app

COPY app.jar app.jar

EXPOSE 8533

ENTRYPOINT ["java", "-jar", "app.jar"]