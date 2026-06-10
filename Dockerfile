FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y python3 ffmpeg curl && \
    rm -rf /var/lib/apt/lists/*

# Need Deno to make yt-dlp work properly since it
# needs js
RUN curl -fsSL https://deno.land/install.sh | sh
ENV PATH="/root/.deno/bin:${PATH}"

WORKDIR /app

COPY app.jar app.jar

RUN ln -s /usr/local/bin/yt-dlp /app/yt-dlp

EXPOSE 8533

ENTRYPOINT ["java", "-jar", "app.jar"]