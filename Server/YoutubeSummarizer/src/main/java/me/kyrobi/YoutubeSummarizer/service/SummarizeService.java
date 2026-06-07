package me.kyrobi.YoutubeSummarizer.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptFormatters;
import io.github.thoroldvix.api.YoutubeClient;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import me.kyrobi.YoutubeSummarizer.client.ProxiedYoutubeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SummarizeService {

    private final HttpClient httpClient;
    private final Gson gson;
    private final Deepseek deepseek;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyUsername;
    private final String proxyPassword;

    private static final String[] VALID_DOMAINS = {
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtu.be", "www.youtu.be", "music.youtube.com"
    };

    private static final String L_PROMPT = "Summarize in 3-5 paragraphs or bullets (~150-250 words). Cover key points and main arguments with enough context. Include important details and implications. Skip minor examples, tangents, redundancy. Structure for easy scanning.";

    private static final String M_PROMPT = "Summarize in 3-5 bullets or 1 short paragraph (~60-100 words). Keep only main takeaways. Minimal context per point. Omit examples, minor details, nuance. Just essential facts.";

    private static final String S_PROMPT = "Summarize in 1-2 sentences (20-80 words). Capture single most important takeaway or core thesis. No supporting details, examples, or background. Direct and punchy.";

    public SummarizeService(Deepseek deepseek,
                            @Value("${proxy.host}") String proxyHost,
                            @Value("${proxy.port}") int proxyPort,
                            @Value("${proxy.username}") String proxyUsername,
                            @Value("${proxy.password}") String proxyPassword) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.deepseek = deepseek;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
    }



    public String summarize(String youtubeLink, String size) {
        if (!isValidLink(youtubeLink)) {
            return "Invalid link!";
        }

        String systemPrompt = switch(size){
            case "L" -> L_PROMPT;
            case "M" -> M_PROMPT;
            case "S" -> S_PROMPT;
            default -> null;
        };

        if (systemPrompt == null) {
            return "Invalid summary type!";
        }

        String videoId = extractVideoId(youtubeLink);
        if (videoId == null) {
            return "Could not extract video ID from link!";
        }

        String transcript = fetchTranscript(videoId);
        if (transcript == null) {
            return "Something went wrong reading this video";
        }
        if (transcript.split("\\s+").length > 15000) {
            return "This video is lowkey too long...";
        }
        String cleaned = removeUselessWords(transcript);
        return deepseek.query(systemPrompt, cleaned);
    }

    private String fetchTranscript(String videoId) {
        Optional<String> transcript = new ProxiedYoutubeClient(proxyHost, proxyPort, proxyUsername, proxyPassword).fetchTranscript(videoId);

        if(transcript.isEmpty()){
            System.out.println("[ERROR] Transcript doesn't exist");
            return "Something went wrong";
        }

        String text = transcript.get();
        return text;
    }

    private String removeUselessWords(String text) {
        // Articles, copula verbs, intensifiers, and common filler words
        String result = Pattern.compile("\\b(the|a|an|is|are|was|were|that|just|like|actually|basically|literally|very|really)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(text)
            .replaceAll("");

        // Uhhhhh ummmmm
        result = Pattern.compile("\\b(u+h+|u+m+|e+r+|a+h+|h+m+)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll("");

        // Filler words
        result = Pattern.compile("\\b(you know|I mean)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll("");

        // Remove soften statements but rarely change the core claim
        result = Pattern.compile("\\b(essentially|honestly|frankly|to be honest|sort of|kind of|somewhat|rather|I guess|I suppose|I assume|I suspect|pretty|quite)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll("");

        // Vague words with no semantic value
        result = Pattern.compile("\\b(or whatever|and stuff|and things|or something)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll("");

        // Bracketed sound tags like [Music], [Applause], [Laughter]
        result = Pattern.compile("\\[[^\\]]+\\]")
            .matcher(result)
            .replaceAll("");

        // URLs and short links
        result = Pattern.compile("https?://\\S+|\\S+\\.\\w+/\\S+")
            .matcher(result)
            .replaceAll("");

        // Repeated words -> collapse to single occurrence ("the the the" -> "the")
        result = Pattern.compile("\\b(\\w+)(\\s+\\1)+\\b", Pattern.CASE_INSENSITIVE)
            .matcher(result)
            .replaceAll("$1");

        // Timestamps like [00:15:30] or (15:30)
        result = Pattern.compile("\\[\\d{2}:\\d{2}(:\\d{2})?\\]|\\(\\d{2}:\\d{2}\\)")
            .matcher(result)
            .replaceAll("");

        return result;
    }

    private boolean isValidLink(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }

        String url = link.strip();

        for (String domain : VALID_DOMAINS) {
            if (url.contains(domain)) {
                return true;
            }
        }

        return false;
    }

    private String extractVideoId(String url) {
        url = url.strip();

        if (url.contains("youtu.be/")) {
            return url.split("youtu.be/")[1].split("\\?")[0].split("/")[0];
        }

        if (url.contains("v=")) {
            int start = url.indexOf("v=") + 2;
            int end = url.indexOf("&", start);
            return end != -1 ? url.substring(start, end) : url.substring(start);
        }

        if (url.contains("/embed/")) {
            return url.split("/embed/")[1].split("\\?")[0];
        }

        if (url.contains("/shorts/")) {
            return url.split("/shorts/")[1].split("\\?")[0];
        }

        return null;
    }
}
