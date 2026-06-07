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

        Path currentDir;
        try{
            // Get the current directory of the current running jar
            currentDir = Paths.get(
                    getClass().getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();

            Path ytdlpPath = currentDir.resolve(binaryName);

            // yt-dlp will run, and the write to this temp file, and then exist
            // and then we can later read the values off the file
            Path tempDir = Files.createTempDirectory("yt-subs-");

            String youtubeURL = "https://www.youtube.com/watch?v=" + videoID;
            String proxyUrl = "http://" + proxyUsername + ":" + proxyPassword + "@" + proxyHost + ":" + proxyPort;

            // Run the binary with the flags passed in
            ProcessBuilder pb = new ProcessBuilder(
                    ytdlpPath.toString(),
                    "--write-auto-subs",
                    "--sub-format", "srt",
                    "--skip-download",
                    "--sub-lang", "en",
                    "--proxy", proxyUrl,
                    "--impersonate", "chrome",
                    // Tells it where to output the transcript file.
                    // In this case, to the temp directory
                    // we specified
                    "-o", tempDir.resolve("%(id)s.%(ext)s").toString(),
                    youtubeURL
            );

            // Merge the error output of the process to the
            // stdout (so you can see in the console)
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // The output is now captured into the string. Used for debugging.
            // Note: This does not contain the transcript. That will be fetched
            // later in the code.
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();

            // Any exist code that is not 0 is an error (usually)
            if(exitCode != 0){
                Files.deleteIfExists(tempDir);
                throw new RuntimeException("yt-dlp failed: " + output);
            }

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


        } catch (URISyntaxException e){
            System.out.println("Error loading jar directory.");
        } catch (IOException e){
            System.out.println("Error opening file (tempfile?)");
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
