package io.kyso;

import com.google.gson.Gson;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TaggedIOException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Command to watch files and run the process
// fswatch -e ".*" -i ".*/[^.]*\\.indexer$" --event Created . | xargs -I '{}' java -jar target/kyso-indexer-jar-with-dependencies.jar {}

public class App {
    private static String[] extensionsToIgnore = { "js", "css", "py", "json", "woff", "woff2" };

    public static boolean isIgnorable(String path) {
        Optional<String> extension = getExtensionByString(path);

        return Arrays.stream(extensionsToIgnore).anyMatch((x -> x.equalsIgnoreCase(extension.get())));
    }

    public static Optional<String> getExtensionByString(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Processing file: " + args[1]);
        System.out.println("Sending indexed documents to: " + args[0]);
        Path path = Paths.get(args[1]);
        String elasticUrl = args[0];

        // We receive a file with paths, so every line is a file to be processed
        List<String> allFiles = Files.readAllLines(path);

        for(String file : allFiles) {
            try {
                if(isIgnorable(file)) {
                    // System.out.println("File " + file + " is ignored");
                } else {
                    // System.out.println("Processing file " + file);
                    Path filePath = Paths.get(file);
                    InputStream stream = Files.newInputStream(filePath);
                    String result = App.extractContentUsingParser(stream);

                    // Save it into Elasticsearch
                    KysoIndex index = new KysoIndex();

                    index.setType("report");
                    index.setContent(result);

                    String[] fileSplitted = file.split("/");
                    String organization = fileSplitted[0];
                    String team = fileSplitted[1];
                    String report = fileSplitted[3];
                    String composedLink = "";

                    for(int i = 4; i < fileSplitted.length; i++) {
                        composedLink = composedLink + "/" + fileSplitted[i];
                    }

                    String finalPath = organization + "/" + team + "/" + report + "?path=" + composedLink.substring(1);

                    index.setLink(finalPath);
                    index.setAuthor(new ArrayList<>());
                    index.setPeople(new ArrayList<>());

                    pushContentToElastic(index, elasticUrl);
                }

            } catch(TaggedIOException ex) {
                if(ex.getMessage().contains("a directory")) {
                    // Is a directory, don't process it
                } else {
                    System.out.println(ex.getMessage());
                    ex.printStackTrace();
                }
            } catch(Exception ex) {
                // Do nothing, just skip that file
            }
        }

        // REMOVE FOLDER
    }

    public static void pushContentToElastic(KysoIndex data, String elasticUrl) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            Gson gson = new Gson();
            String indexAsJson = gson.toJson(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(elasticUrl + "/kyso-index/report"))
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(indexAsJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Uploading to elastic returned: " + response.statusCode());
            System.out.println(response.body());

        } catch(Exception ex) {
            System.out.println("Can't push content to elasticsearch");
            ex.printStackTrace();
        }
    }

    public static String extractContentUsingParser(InputStream stream) throws IOException, TikaException, SAXException {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        parser.parse(stream, handler, metadata, context);
        String trimmedContent = handler.toString().trim().replaceAll("\\s+", " ");

        return trimmedContent;
    }
}
