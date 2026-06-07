package me.kyrobi.YoutubeSummarizer.controller;

import me.kyrobi.YoutubeSummarizer.service.SummarizeService;
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

    public SummarizeEndpointController(SummarizeService summarizeService) {
        this.summarizeService = summarizeService;
    }

    @GetMapping("/summarize")
    public Map<String, String> summarize(@RequestParam String youtubeLink, @RequestParam String size){

        if(!isValidLink(youtubeLink)){
            return Map.of("summary", "Invalid Link!");
        }

        if(!isValidSize(size)){
            return Map.of("summary", "Invalid Menu Selection!");
        }

        String summary = summarizeService.summarize(youtubeLink, size);
        System.out.println(summary);
        return Map.of("summary", summary);
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
