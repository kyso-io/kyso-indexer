package io.kyso;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import java.util.stream.Collectors;

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
        String mode = "index";

        if(args.length == 3) {
            if(args[2].equalsIgnoreCase("--reindex")) {
                mode = "reindex";
            } else {
                System.out.println("Option not recognized, do you mean --reindex?");
                return;
            }
        }

        Path path = Paths.get(args[1]);
        String elasticUrl = args[0];

        if(mode.equalsIgnoreCase("index")) {
            // We receive a file with paths, so every line is a file to be processed
            List<String> allFiles = Files.readAllLines(path);
            List<KysoIndex> bulkInsert = new ArrayList<>();

            Map<String, Object> kysoMap = findKysoYamlOrJson(allFiles);

            for(String file : allFiles) {
                file = "/data" + file;

                try {
                    String fileAbsolutePath = file;
                    KysoIndex index = processFile(fileAbsolutePath, kysoMap);

                    if(index != null) {
                        bulkInsert.add(index);
                    }

                } catch(Exception ex) {
                    System.out.println("Cant process file " + file);
                    ex.printStackTrace();
                }
            }

            System.out.println("----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
            // Save into elastic
            bulkInsert.forEach(item -> pushContentToElastic(item, elasticUrl));

            // Delete folder
            deleteFolder(new File(args[1]), allFiles);
        } else {
            // reindex
            System.out.println("Reindexing");

            // First get all organizations folders, which are in the base of the received path
            File[] allOrganizationsFolders = new File(args[1]).listFiles(File::isDirectory);

            // Process organizations
            for(File organizationFolder : allOrganizationsFolders) {
                String organizationSlug = organizationFolder.getName();
                System.out.println("Processing organization " + organizationSlug);

                // Get all teams folders
                File[] allTeamsFolders = organizationFolder.listFiles(File::isDirectory);

                // Process team
                for(File teamFolder : allTeamsFolders) {
                    String teamSlug = teamFolder.getName();
                    System.out.println("----> Processing team " + teamSlug);
                    String reportsAbsolutePath = teamFolder.getAbsolutePath() + "/reports";

                    // Get all the reports of the team
                    File[] allReportsFolders = new File(reportsAbsolutePath).listFiles(File::isDirectory);

                    for(File reportFolder : allReportsFolders) {
                        String reportSlug = reportFolder.getName();
                        System.out.println("--------> Processing report " + reportSlug);

                        // Get all versions of the report, and take the most newer (higher)
                        File[] allVersionsFolders = reportFolder.listFiles(File::isDirectory);

                        File maxVersion = Arrays.stream(allVersionsFolders).max(Comparator.comparing(File::getName)).orElse(null);

                        System.out.println("------------> Newer version is: " + maxVersion.getName());

                        // Find kyso.yaml,yml,json
                        File[] allRegularFilesInRootReport = maxVersion.listFiles(File::isFile);

                        Map<String, Object> kysoMap = findKysoYamlOrJson(allRegularFilesInRootReport);

                        // Process all the files, subfolders, subfiles... whatever and process them
                        List<Path> allFilesOfFolder = Files.walk(Paths.get(maxVersion.getAbsolutePath()))
                                .filter(Files::isRegularFile)
                                .toList();

                        List<KysoIndex> bulkInsert = new ArrayList<>();

                        for(Path file : allFilesOfFolder) {
                            try {
                                String fileAbsolutePath = file.toFile().getAbsolutePath();
                                KysoIndex index = processFile(fileAbsolutePath, kysoMap);

                                if(index != null) {
                                    bulkInsert.add(index);
                                }

                            } catch(Exception ex) {
                                System.out.println("Cant process file " + file.toFile().getAbsolutePath());
                                ex.printStackTrace();
                            }
                        }

                        // Save into elastic
                        System.out.println("----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
                        bulkInsert.forEach(item -> pushContentToElastic(item, elasticUrl));
                    }
                }
            }
        }
    }

    public static void deleteFolder(File enclosingFolder, List<String> filesToDelete) {
        try {
            FileUtils.forceDelete(enclosingFolder);
        } catch(Exception ex) {
            System.out.println("Error deleting enclosing folder");
            ex.printStackTrace();
        }

        for(String file : filesToDelete) {
            try {
                FileUtils.forceDelete(new File("/data" + file));
                System.out.println("Deleted " + "/data" + file);
            } catch(Exception ex) {
                // silent
            }
        }
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

    public static Map<String, Object> findKysoYamlOrJson(File[] allRegularFilesInRootReport) {
        Map<String, Object> kysoMap = new HashMap<>();

        for(File regularFile : allRegularFilesInRootReport) {
            if(regularFile.getName().equalsIgnoreCase("kyso.yaml") ||
                    regularFile.getName().equalsIgnoreCase("kyso.yml") ||
                    regularFile.getName().equalsIgnoreCase("kyso.json") ) {
                Path filePath = Paths.get(regularFile.getAbsolutePath());

                System.out.println("------------> Reading kyso file " + filePath.toString());
                kysoMap = readKysoFile(filePath);
            }
        }

        return kysoMap;
    }

    public static Map<String, Object> findKysoYamlOrJson(List<String> filePaths) {
        Map<String, Object> kysoMap = new HashMap<>();

        for(String regularFile : filePaths) {
            if(regularFile.endsWith("kyso.yaml") ||
               regularFile.endsWith("kyso.yml") ||
               regularFile.endsWith("kyso.json") ) {
                Path filePath = Paths.get(regularFile);

                System.out.println("------------> Reading kyso file " + filePath.toString());
                kysoMap = readKysoFile(filePath);
            }
        }

        return kysoMap;
    }

    public static KysoIndex processFile(String file, Map<String, Object> kysoMap) {
        KysoIndex index = new KysoIndex();

        try {
            if(isIgnorable(file)) {
                // System.out.println("File " + file + " is ignored");
                return null;
            } else {
                System.out.println("------------> Processing file " + file);

                Path filePath = Paths.get(file);
                InputStream stream = Files.newInputStream(filePath);
                String result = App.extractContentUsingParser(stream);

                index.setType("report");
                index.setContent(result);
                index.setFilePath(file);

                String[] fileSplitted = file.split("/");
                String organization = fileSplitted[2];
                String team = fileSplitted[3];
                String report = fileSplitted[5];
                String composedLink = "";
                String version = fileSplitted[6];
                int intVersion = -1;

                try {
                    intVersion = Integer.parseInt(version);
                } catch(Exception ex) {
                    // silent
                }

                for(int i = 7; i < fileSplitted.length; i++) {
                    composedLink = composedLink + "/" + fileSplitted[i];
                }

                String finalPath = organization + "/" + team + "/" + report + "?path=" + composedLink.substring(1) + "&version=" + version;

                index.setVersion(intVersion);
                index.setLink(finalPath);
                index.setOrganizationSlug(organization);
                index.setTeamSlug(team);
                index.setEntityId(report);

                // Open kyso.json, kyso.yaml or kyso.yml and retrieve that information from there if exists
                index.setTags("");
                index.setPeople("");
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
            ex.printStackTrace();
        }

        Map<String, Object> finalKysoMap = kysoMap;
        if(kysoMap.containsKey("tags")) {
            index.setTags(finalKysoMap.get("tags").toString());
        }

        if(kysoMap.containsKey("authors")) {
            index.setPeople((finalKysoMap.get("authors").toString()));
        }

        if(kysoMap.containsKey("organization")) {
            index.setOrganizationSlug(finalKysoMap.get("organization").toString());
        }

        if(kysoMap.containsKey("team")) {
            index.setTeamSlug(finalKysoMap.get("team").toString());
        }

        if(kysoMap.containsKey("title")) {
            index.setTitle(finalKysoMap.get("title").toString());
        }

        return index;
    }

    public static void deleteCurrentVersionIndex(KysoIndex data, String elasticUrl) {
        try {
            String query = """
                        {
                            "query": {
                                "bool": {
                                    "must": [
                                        {
                                            "term": {
                                                "filePath.keyword": "%%FILEPATH%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "version": %%VERSION%%
                                            }
                                        }
                                    ]
                                }
                            }
                         }
                    """;

            query = query.replace("%%FILEPATH%%", data.getFilePath())
                    .replace("%%VERSION%%", String.valueOf(data.getVersion()));

            URI uri = new URI(elasticUrl + "/kyso-index/_delete_by_query");
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Gson gson = new Gson();
            JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
            int deleted = responseBody.get("deleted").getAsInt();

            System.out.println("----------------------------> Deleted " + deleted + " older indexes for " + data.getFilePath() );

        } catch(Exception ex) {
            System.out.println("----------------------------> " + data.getLink() + " error deleting older versions");
            ex.printStackTrace();
        }
    }

    public static boolean existsIndexInElastic(KysoIndex data, String elasticUrl) {
        try {
            String query = """
                        {
                            "query": {
                                "bool": {
                                    "must": [
                                        {
                                            "term": {
                                                "filePath.keyword": "%%FILEPATH%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "version": %%VERSION%%
                                            }
                                        }
                                    ]
                                }
                            }
                         }
                    """;

            query = query.replace("%%FILEPATH%%", data.getFilePath())
                    .replace("%%VERSION%%", String.valueOf(data.getVersion()));

            URI uri = new URI(elasticUrl + "/kyso-index/report/_search");
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Gson gson = new Gson();
            JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
            int results = responseBody.get("hits").getAsJsonObject().get("total").getAsJsonObject().get("value").getAsInt();

            if(results > 0) {
                return true;
            } else {
                return false;
            }
        } catch(Exception ex) {
            // System.out.println("--------------------> " + data.getLink() + " can't push content to elasticsearch");
            // ex.printStackTrace();
            return false;
        }
    }

    public static void pushContentToElastic(KysoIndex data, String elasticUrl) {
        try {
            // Delete previous results for the same version, as we are going to save it again anyways
            deleteCurrentVersionIndex(data, elasticUrl);

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

            System.out.println("--------------------> " + data.getLink() + " upload to elastic returned: " + response.statusCode());
        } catch(Exception ex) {
            System.out.println("--------------------> " + data.getLink() + " can't push content to elasticsearch");
            ex.printStackTrace();
        }
    }

    public static String extractContentUsingParser(InputStream stream) throws IOException, TikaException, SAXException {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        parser.parse(stream, handler, metadata, context);
        String trimmedContent = handler.toString().trim().replaceAll("\\s+", " ");

        return trimmedContent;
    }
}
