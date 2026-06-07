package me.kyrobi.YoutubeSummarizer.controller;

import jakarta.servlet.http.HttpServletRequest;
import me.kyrobi.YoutubeSummarizer.service.SummarizeService;
import me.kyrobi.YoutubeSummarizer.service.TurnstileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@RestController
@CrossOrigin("https://videosummary.kyrobi.net")
public class SummarizeEndpointController {

    private final SummarizeService summarizeService;
    private final TurnstileService turnstileService;

    public SummarizeEndpointController(SummarizeService summarizeService, TurnstileService turnstileService) {
        this.summarizeService = summarizeService;
        this.turnstileService = turnstileService;
    }

    @GetMapping("/summarize")
    public ResponseEntity<Map<String, String>> summarize(
            @RequestParam String youtubeLink,
            @RequestParam String size,
            @RequestParam("cf-turnstile-response") String turnstileToken,
            HttpServletRequest request) {

        String ip = request.getHeader("CF-Connecting-IP");
        if(ip == null){
            ip = request.getRemoteAddr();
        }

        if(!turnstileService.validateToken(turnstileToken, ip)){
            return ResponseEntity.status(403).body(Map.of("error", "Verification failed"));
        }

        if(!isValidLink(youtubeLink)){
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Link!"));
        }

        if(!isValidSize(size)){
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Menu Selection!"));
        }

        String summary = summarizeService.summarize(youtubeLink, size);
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    private boolean isValidLink(String youtubeLink){
        if(youtubeLink == null){
            return false;
        }

        if(youtubeLink.isBlank()){
            return false;
        }

        // Random 100 length limit
        if(youtubeLink.length() > 100){
            return false;
        }

        try{
            // Get just the domain, even if link
            // passed in as subdomain.
            // i.e. "https://youtu.be/abc" ->  "youtu.be"
            String host = new URI(youtubeLink).getHost();
            if(host == null){
                return false;
            }

            if (
                    host.contains("youtube.com") ||
                    host.contains("youtu.be") ||
                    host.contains("www.youtube.com") ||
                    host.contains("m.youtube.com")
            )  {
                return true;
            }

            return false;


        } catch (Exception e) {
            return false;
        }

    }

    List<String> validSize = new ArrayList<>(List.of("S", "M", "L"));
    private boolean isValidSize(String size){
        if(size == null){
             return false;
        }

        if(size.length() > 1){
            return false;
        }

        if(!validSize.contains(size)){
            return false;
        }

        return true;
    }
}
