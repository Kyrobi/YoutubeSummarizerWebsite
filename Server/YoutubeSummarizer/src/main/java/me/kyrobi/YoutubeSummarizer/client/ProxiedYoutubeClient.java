package me.kyrobi.YoutubeSummarizer.client;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ProxiedYoutubeClient {

    private final String proxyHost;
    private final int proxyPort;
    private final String proxyUsername;
    private final String proxyPassword;

    public ProxiedYoutubeClient(String proxyHost, int proxyPort, String proxyUsername, String proxyPassword){
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
    }

    public Optional<String> fetchTranscript(String videoID){

        // Since my dev environment is windows and my prod is linux,
        // it's just easier to have a system that automatically
        // uses the correct yt-dlp binary
        boolean isWindow = System.getProperty("os.name").toLowerCase().contains("win");
        String binaryName = isWindow ? "yt-dlp.exe" : "yt-dlp";

        try{
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            Path ytdlpPath = currentDir.resolve(binaryName);

            // yt-dlp will run, and the write to this temp file, and then exist
            // and then we can later read the values off the file
            Path tempDir = Files.createTempDirectory("yt-subs-");

            String youtubeURL = "https://www.youtube.com/watch?v=" + videoID;
            String proxyUrl = "http://" + proxyUsername + ":" + proxyPassword + "@" + proxyHost + ":" + proxyPort;

            // One call: --print title goes to stdout, subtitles go to .srt file
            ProcessBuilder pb = new ProcessBuilder(
                    ytdlpPath.toString(),
                    "--write-auto-subs",
                    "--sub-format", "srt",
                    "--skip-download",
                    "--sub-lang", "en",
                    "--proxy", proxyUrl,
                    "--impersonate", "chrome",
                    "--print", "title",
                    "-o", tempDir.resolve("%(id)s.%(ext)s").toString(),
                    youtubeURL
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            // First line of stdout is the title, rest are yt-dlp log messages
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();

            // Any exit code that is not 0 is an error (usually)
            if(exitCode != 0){
                Files.deleteIfExists(tempDir);
                throw new RuntimeException("yt-dlp failed: " + output);
            }

            // Split title from log lines
            String[] lines = output.split("\n", 2);
            String videoTitle = lines[0].trim();
            System.out.println("[VIDEO] " + videoTitle + " (" + videoID + ")");

            /*
            --------------------------------------------
            Now we have the srt file, we can now read it
            --------------------------------------------
             */
            // We read from the tempfile location since
            // yt-dlp writes to the temp folder
            List<Path> srtFiles;
            // Go through the directory and find any .srt files
            // that were created. Put in try for auto cleanup
            try(Stream<Path> files = Files.list(tempDir)){
                srtFiles = files.filter(f -> f.toString().endsWith(".srt")).toList();
            }

            if(srtFiles.isEmpty()){
                throw new RuntimeException("No subtitles found for video " + videoID);
            }

            String srt = Files.readString(srtFiles.getFirst());
            // Delete all the files inside the directory if exists
            for(Path f: Files.list(tempDir).toList()){
                Files.deleteIfExists(f);
            }
            // A folder needs to be emptied before it can be deleted
            Files.deleteIfExists(tempDir);

            return Optional.of(parseSrt(srt));

        } catch (IOException e){
            System.out.println("Error opening file " + e.getMessage());
        } catch (InterruptedException e){
            System.out.println("yt-dlp process was interrupted");
        }

        return Optional.empty();
    }

    private String parseSrt(String srt) {
        return srt
                .replaceAll("(?m)^\\d+\\s*$", "")                    // index numbers
                .replaceAll("(?m)^\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*$", "") // timestamps
                .replaceAll("<[^>]+>", "")                            // HTML tags
                .replaceAll("(?m)^[\\s]*$", "")                      // blank lines
                .replaceAll("\\s+", " ")                             // collapse spaces
                .trim();
    }

}
