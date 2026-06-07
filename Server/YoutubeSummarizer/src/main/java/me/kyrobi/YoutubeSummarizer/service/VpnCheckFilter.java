package me.kyrobi.YoutubeSummarizer.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Order(2)
public class VpnCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VpnCheckFilter.class);
    private static final double VPN_THRESHOLD = 0.95;
    private static final long CACHE_TTL_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final int MAX_CACHE_SIZE = 10_000;
    private static final String CACHE_FILE_NAME = "vpn_cache.json";

    private final HttpClient httpClient;
    private final Gson gson;
    private final File cacheFile;
    private final String contactEmail;
    private final ConcurrentHashMap<String, CacheEntry> ipCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final double score;
        final long timestamp;

        CacheEntry(double score) {
            this.score = score;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public VpnCheckFilter(@Value("${vpn.contact.email}") String contactEmail) {
        this.contactEmail = contactEmail;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cacheFile = new File(CACHE_FILE_NAME);
    }

    @PostConstruct
    public void init() {
        loadCache();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {

        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }

        CacheEntry cached = ipCache.get(ip);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MILLIS) {
            if (cached.score >= VPN_THRESHOLD) {
                blockRequest(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        try {
            double score = checkVpn(ip);
            ipCache.put(ip, new CacheEntry(score));
            if (score >= VPN_THRESHOLD) {
                blockRequest(response);
                return;
            }
        } catch (Exception e) {
            ipCache.put(ip, new CacheEntry(0.0));
            log.warn("VPN check failed for {}: {}", ip, e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private void blockRequest(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"VPNs and proxies are not allowed\"}");
    }

    private double checkVpn(String ip) throws IOException, InterruptedException {
        String apiUrl = String.format(
                "https://check.getipintel.net/check.php?ip=%s&contact=%s&flags=b&oflags=a&format=json",
                ip, contactEmail
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> root = gson.fromJson(response.body(), type);

            String status = (String) root.getOrDefault("status", "");
            if ("success".equalsIgnoreCase(status)) {
                String resultStr = root.getOrDefault("result", "0").toString();
                return Double.parseDouble(resultStr);
            } else {
                String message = (String) root.getOrDefault("message", "Unknown error");
                log.warn("getipintel.net API error: {}", message);
                return 0.0;
            }
        } catch (Exception e) {
            log.error("getipintel.net parsing failed: {}", e.getMessage());
            return 0.0;
        }
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            saveCache();
            return;
        }

        try (Reader reader = new FileReader(cacheFile)) {
            Type type = new TypeToken<ConcurrentHashMap<String, Double>>() {}.getType();
            Map<String, Double> loadedData = gson.fromJson(reader, type);
            if (loadedData != null) {
                for (Map.Entry<String, Double> entry : loadedData.entrySet()) {
                    ipCache.put(entry.getKey(), new CacheEntry(entry.getValue()));
                }
                log.info("Loaded {} IPs from {}", ipCache.size(), CACHE_FILE_NAME);
            }
        } catch (IOException e) {
            log.warn("Could not load {}: {}", CACHE_FILE_NAME, e.getMessage());
        }
    }

    private void saveCache() {
        try {
            if (cacheFile.getParentFile() != null && !cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }

            ConcurrentHashMap<String, Double> saveData = new ConcurrentHashMap<>();
            for (Map.Entry<String, CacheEntry> entry : ipCache.entrySet()) {
                saveData.put(entry.getKey(), entry.getValue().score);
            }

            try (Writer writer = new FileWriter(cacheFile)) {
                gson.toJson(saveData, writer);
            }
        } catch (IOException e) {
            log.warn("Could not save {}: {}", CACHE_FILE_NAME, e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void pruneCache() {
        long now = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<String, CacheEntry>> it = ipCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> entry = it.next();
            if ((now - entry.getValue().timestamp) > CACHE_TTL_MILLIS) {
                it.remove();
                removed++;
            }
        }

        if (ipCache.size() > MAX_CACHE_SIZE) {
            ipCache.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((a, b) -> Long.compare(a.timestamp, b.timestamp)))
                    .limit(ipCache.size() - MAX_CACHE_SIZE)
                    .forEach(entry -> ipCache.remove(entry.getKey()));
        }

        if (removed > 0) {
            log.info("Pruned {} expired IPs from VPN cache. Remaining: {}", removed, ipCache.size());
        }

        saveCache();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/summarize");
    }
}
