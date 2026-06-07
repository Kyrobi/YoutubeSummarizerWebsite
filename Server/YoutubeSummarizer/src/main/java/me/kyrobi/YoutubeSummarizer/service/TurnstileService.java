package me.kyrobi.YoutubeSummarizer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TurnstileService {

    private final String secretKey;
    private final RestTemplate restTemplate;

    public TurnstileService(@Value("${turnstile.secret.key}") String secretKey) {
        this.secretKey = secretKey;
        this.restTemplate = new RestTemplate();
    }

    public boolean validateToken(String token, String remoteip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", token);
        if (remoteip != null) {
            params.add("remoteip", remoteip);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<TurnstileResponse> response = restTemplate.postForEntity(
                    "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                    request, TurnstileResponse.class);
            return response.getBody() != null && response.getBody().success;
        } catch (Exception e) {
            return false;
        }
    }

    private static class TurnstileResponse {
        private boolean success;
        public void setSuccess(boolean success) { this.success = success; }
    }
}
