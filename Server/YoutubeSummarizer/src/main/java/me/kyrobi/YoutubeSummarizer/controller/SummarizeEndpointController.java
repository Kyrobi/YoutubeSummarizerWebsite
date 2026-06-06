package me.kyrobi.YoutubeSummarizer.controller;

import me.kyrobi.YoutubeSummarizer.service.SummarizeService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin("*")
public class SummarizeEndpointController {

    private final SummarizeService summarizeService;

    public SummarizeEndpointController(SummarizeService summarizeService) {
        this.summarizeService = summarizeService;
    }

    @GetMapping("/summarize")
    public Map<String, String> summarize(@RequestParam String youtubeLink, @RequestParam String size) {
        String summary = summarizeService.summarize(youtubeLink, size);
        System.out.println(summary);
        return Map.of("summary", summary);
    }
}
