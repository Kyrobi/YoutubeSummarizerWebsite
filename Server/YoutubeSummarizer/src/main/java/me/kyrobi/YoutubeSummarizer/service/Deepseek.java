package me.kyrobi.YoutubeSummarizer.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class Deepseek {

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public Deepseek(@Value("${deepseek.api.key}") String apiKey) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.apiKey = apiKey;

    }

    public String query(String systemPrompt, String userContent) {
        try {
            DeepSeekRequestBody body = new DeepSeekRequestBody(systemPrompt, userContent);
            String requestBody = gson.toJson(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "DeepSeek API error (HTTP " + response.statusCode() + "): " + response.body();
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

            JsonObject message = root.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message");
            String content = message.get("content").getAsString();

            JsonObject usage = root.getAsJsonObject("usage");
            double promptTokens = usage.get("prompt_tokens").getAsDouble();
            double completionTokens = usage.get("completion_tokens").getAsDouble();


            calculateCost(promptTokens, completionTokens);

            return content;

        } catch (Exception e) {
            return "Error calling DeepSeek API: " + e.getMessage();
        }
    }

    private void calculateCost(double promptTokens, double completionTokens) {
        double promptCost = (promptTokens / 1_000_000) * 0.14;
        double completionCost = (completionTokens / 1_000_000) * 0.28;
        double totalCost = promptCost + completionCost;
        double totalTokens = promptTokens + completionTokens;

        System.out.println("--- Token Usage ---");
        System.out.println("Prompt tokens: " + (int) promptTokens);
        System.out.println("Completion tokens: " + (int) completionTokens);
        System.out.println("Total tokens: " + (int) totalTokens);
        System.out.println("Cost: $" + String.format("%.6f", totalCost));
    }

    private record DeepSeekRequestBody(
        List<Message> messages,
        String model,
        Thinking thinking,
        String reasoningEffort,
        int maxTokens,
        ResponseFormat responseFormat,
        Object stop,
        boolean stream,
        Object streamOptions,
        double temperature,
        double topP,
        Object tools,
        String toolChoice,
        boolean logprobs,
        Object topLogprobs
    ) {
        DeepSeekRequestBody(String systemPrompt, String userContent) {
            this(
                List.of(
                    new Message("system", systemPrompt),
                    new Message("user", userContent)
                ),
                "deepseek-v4-flash",
                new Thinking("disabled"),
                "low",
                2048,
                new ResponseFormat("text"),
                null,
                false,
                null,
                1,
                1,
                null,
                "none",
                false,
                null
            );
        }
    }

    private record Message(String role, String content) {}
    private record Thinking(String type) {}
    private record ResponseFormat(String type) {}
}
