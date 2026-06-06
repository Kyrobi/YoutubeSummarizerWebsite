package me.kyrobi.YoutubeSummarizer.client;

import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeClient;
import okhttp3.*;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;

public class ProxiedYoutubeClient implements YoutubeClient {

    private final OkHttpClient httpClient;

    public ProxiedYoutubeClient(String proxyHost, int proxyPort, String username, String password) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));

        this.httpClient = new OkHttpClient.Builder()
            .proxy(proxy)
            .proxyAuthenticator((route, response) -> {
                String credentials = Credentials.basic(username, password);
                return response.request().newBuilder()
                    .header("Proxy-Authorization", credentials)
                    .build();
            })
            .build();
    }

    @Override
    public String get(String url, Map<String, String> headers) throws TranscriptRetrievalException {
        Headers.Builder headerBuilder = new Headers.Builder();
        headers.forEach(headerBuilder::add);

        Request request = new Request.Builder()
            .url(url)
            .headers(headerBuilder.build())
            .get()
            .build();

        return execute(request);
    }

    @Override
    public String post(String url, String json) throws TranscriptRetrievalException {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        return execute(request);
    }

    private String execute(Request request) throws TranscriptRetrievalException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new TranscriptRetrievalException("HTTP " + response.code() + " - " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        } catch (TranscriptRetrievalException e) {
            throw e;
        } catch (Exception e) {
            throw new TranscriptRetrievalException("Request failed: " + e.getMessage(), e);
        }
    }
}
