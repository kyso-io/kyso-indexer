package io.kyso;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TaggedIOException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.json.simple.parser.ParseException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Command to watch files and run the process
// fswatch -e ".*" -i ".*/[^.]*\\.indexer$" --event Created . | xargs -I '{}' java -jar target/kyso-indexer-jar-with-dependencies.jar http://localhost:9200 {}

public class App {
    private static String[] extensionsToIgnore = { "js", "css", "py", "woff", "woff2", "scss", "java", "jpg", "jpeg",
            "png", "svg", "gif", "eot", "ttf" };

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
        System.out.println("Using: " + args[0]);
        System.out.println("Ignoring files: " + extensionsToIgnore.toString());
        Path path = Paths.get(args[1]);
        String elasticUrl = args[0];
        Map<String, Object> kysoMap = new HashMap<>();

        // We receive a file with paths, so every line is a file to be processed
        List<String> allFiles = Files.readAllLines(path);
        List<KysoIndex> bulkInsert = new ArrayList<>();

        for(String file : allFiles) {
            file = "/data" + file;

            System.out.println("Processing file: " + file);
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
                    String organization = fileSplitted[2];
                    String team = fileSplitted[3];
                    String report = fileSplitted[5];
                    String composedLink = "";
                    String version = fileSplitted[6];

                    for(int i = 7; i < fileSplitted.length; i++) {
                        composedLink = composedLink + "/" + fileSplitted[i];
                    }

                    String finalPath = organization + "/" + team + "/" + report + "?path=" + composedLink.substring(1) + "&version=" + version;

                    String filename = filePath.getFileName().toString();
                    if(filename.equalsIgnoreCase("kyso.yaml") ||
                       filename.equalsIgnoreCase("kyso.yml") ||
                       filename.equalsIgnoreCase("kyso.json")) {
                        System.out.println("Reading kyso file " + filePath.toString());
                        kysoMap = readKysoFile(filePath);
                    }

                    index.setLink(finalPath);
                    index.setOrganizationSlug(organization);
                    index.setTeamSlug(team);
                    index.setEntityId(report);

                    // Open kyso.json, kyso.yaml or kyso.yml and retrieve that information from there if exists
                    index.setTags("");
                    index.setPeople("");

                    bulkInsert.add(index);
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

        Map<String, Object> finalKysoMap = kysoMap;
        if(kysoMap.containsKey("tags")) {
            bulkInsert.forEach(item -> item.setTags(finalKysoMap.get("tags").toString()));
        }

        if(kysoMap.containsKey("authors")) {
            bulkInsert.forEach(item -> item.setPeople((finalKysoMap.get("authors").toString())));
        }

        if(kysoMap.containsKey("organization")) {
            bulkInsert.forEach(item -> item.setOrganizationSlug(finalKysoMap.get("organization").toString()));
        }

        if(kysoMap.containsKey("team")) {
            bulkInsert.forEach(item -> item.setTeamSlug(finalKysoMap.get("team").toString()));
        }

        if(kysoMap.containsKey("title")) {
            bulkInsert.forEach(item -> item.setTitle(finalKysoMap.get("title").toString()));
        }

        System.out.println("Uploading to Elastic " + bulkInsert.size() + " registries");
        // Save into elastic
        bulkInsert.forEach(item -> pushContentToElastic(item, elasticUrl));

        // Delete folder
        deleteFiles(new File(args[1]), allFiles);
    }

    public static void deleteFiles(File kysoIndexerFile, List<String> allFiles) {
        //Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // TimeUnit.SECONDS.sleep(30);
                FileUtils.forceDelete(kysoIndexerFile);

                for (String file : allFiles) {
                    try {
                        FileUtils.forceDelete(new File("/data" + file));
                        System.out.println("Deleted " + "/data" + file);
                    } catch (Exception ex) {
                        // silent
                    }
                }
            } catch (Exception ex) {
                // silent
            }
        //});
    }

    public static Map<String, Object> readKysoFile(Path kysoFilePath) {
        Map kyso = Collections.emptyMap();
        String filename = kysoFilePath.getFileName().toString();

        try {
            InputStream stream = Files.newInputStream(kysoFilePath);

            if (filename.equalsIgnoreCase("kyso.yaml") ||
                filename.equalsIgnoreCase("kyso.yml")) {
                YamlReader reader = new YamlReader(new InputStreamReader(stream));
                Object object = reader.read();

                kyso = (Map)object;
            } else {
                // It's a JSON
                try (Reader reader = new InputStreamReader(stream)) {
                    //Read JSON file
                    Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                    kyso = new Gson().fromJson(reader, mapType);
                } catch (Exception e) {
                    e.printStackTrace();
                    return kyso;
                }
            }

            return kyso;
        } catch (Exception ex) {
            System.out.println("Can't read kyso file");
            return kyso;
        }
    }

    public static void pushContentToElastic(KysoIndex data, String elasticUrl) {
        try {
            URI uri = new URI(elasticUrl + "/kyso-index/report");
            HttpClient client = HttpClient.newHttpClient();

            Gson gson = new Gson();
            String indexAsJson = gson.toJson(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
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
