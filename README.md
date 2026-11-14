# YouTube Summarizer

AI-powered YouTube video summarizer. Paste a video URL, pick a summary length, and get a transcript-based summary generated with the DeepSeek API.

**Live:** https://videosummary.kyrobi.net/

<img width="869" height="490" alt="YouTube Summarizer UI" src="https://github.com/user-attachments/assets/bfc4fa55-6563-4b17-b745-4dab8a288194" />

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Transcript extraction | yt-dlp + ffmpeg, Deno runtime |
| Summarization | DeepSeek API |
| Caching | SQLite (per-video summary cache) |
| Rate limiting | Bucket4j, per-IP token buckets |
| Bot protection | Cloudflare Turnstile |
| Frontend | Svelte + Vite |
| Infra | Docker, GitHub Actions, OVH VPS, nginx + TLS |

## How it works

1. The client posts a YouTube URL, a summary size (`S`, `M`, `L`), and a Cloudflare Turnstile token.
2. The server checks the Turnstile token against the client IP.
3. The URL is parsed and checked against an allowlist of YouTube domains. The size has to be one of the three accepted values.
4. A per-IP token bucket rejects requests over the rate limit before any expensive work runs.
5. The transcript is fetched with yt-dlp. If a cached summary exists for that video and size, it is returned right away.
6. The transcript and a size-specific prompt go to the DeepSeek API.
7. The summary is cached in SQLite and returned to the client.

## Project layout

```
Server/YoutubeSummarizer/   Spring Boot service
  controller/               HTTP endpoint + input validation
  service/                  Summarization, DeepSeek client, caching, rate limiting, Turnstile
  client/                   Proxied YouTube client
Client/my-app/              Svelte frontend
Dockerfile                  Runtime image (JRE 21 + yt-dlp + Deno + ffmpeg)
docker-compose.yml          Deployment definition
.github/workflows/deploy.yml
```

## Running locally

```bash
cd Server/YoutubeSummarizer
./mvnw spring-boot:run
```

The service reads configuration from `application.properties` (DeepSeek key, proxy settings, Turnstile secret). Local runs need valid values for those before the `/summarize` endpoint will respond.

Frontend:

```bash
cd Client/my-app
npm install
npm run dev
```

## Deployment

Pushes to `main` trigger GitHub Actions, which builds the Maven artifact, builds the Docker image, pushes it to Docker Hub, and redeploys the OVH VPS via `docker-compose`. nginx terminates TLS in front of the container.

## Notes

- Summaries are cached per video ID and size, so asking for the same video twice never hits the LLM twice.
- Rate limiting and Turnstile run before the expensive path.
- yt-dlp needs Deno for JavaScript extraction, which is why the runtime image installs it.
