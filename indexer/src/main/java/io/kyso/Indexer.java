package io.kyso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.TaggedIOException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class Indexer {
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

    public static void deleteFolder(File enclosingFolder, List<String> filesToDelete) {
        try {
            FileUtils.forceDelete(enclosingFolder);
        } catch (Exception ex) {
            System.out.println("Error deleting enclosing folder");
            ex.printStackTrace();
        }

        for (String file : filesToDelete) {
            try {
                FileUtils.forceDelete(new File("/data" + file));
                System.out.println("Deleted " + "/data" + file);
            } catch (Exception ex) {
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

                kyso = (Map) object;
            } else {
                // It's a JSON
                try (Reader reader = new InputStreamReader(stream)) {
                    // Read JSON file
                    Type mapType = new TypeToken<Map<String, Object>>() {
                    }.getType();
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

        for (File regularFile : allRegularFilesInRootReport) {
            if (regularFile.getName().equalsIgnoreCase("kyso.yaml") ||
                    regularFile.getName().equalsIgnoreCase("kyso.yml") ||
                    regularFile.getName().equalsIgnoreCase("kyso.json")) {
                Path filePath = Paths.get(regularFile.getAbsolutePath());

                System.out.println("------------> Reading kyso file " + filePath.toString());
                kysoMap = readKysoFile(filePath);
            }
        }

        return kysoMap;
    }

    public static Map<String, Object> findKysoYamlOrJsonByPath(List<Path> allRegularFilesInRootReport) {
        Map<String, Object> kysoMap = new HashMap<>();

        for (Path regularFile : allRegularFilesInRootReport) {
            if (regularFile.getFileName().toString().equalsIgnoreCase("kyso.yaml") ||
                    regularFile.getFileName().toString().equalsIgnoreCase("kyso.yml") ||
                    regularFile.getFileName().toString().equalsIgnoreCase("kyso.json")) {

                System.out.println("------------> Reading kyso file " + regularFile.toAbsolutePath().toString());
                kysoMap = readKysoFile(regularFile);
            }
        }

        return kysoMap;
    }

    public static Map<String, Object> findKysoYamlOrJson(List<String> filePaths) {
        Map<String, Object> kysoMap = new HashMap<>();

        for (String regularFile : filePaths) {
            if (regularFile.endsWith("kyso.yaml") ||
                    regularFile.endsWith("kyso.yml") ||
                    regularFile.endsWith("kyso.json")) {
                Path filePath = Paths.get(regularFile);

                System.out.println("------------> Reading kyso file " + filePath.toString());
                kysoMap = readKysoFile(filePath);
            }
        }

        return kysoMap;
    }

    public static KysoIndex processFile(String basePath, String file, Organization organization, Team team,
            Report report, String version, ArrayList<User> users, ArrayList<Tag> tags, Map<String, Object> kysoMap) {
        KysoIndex index = new KysoIndex();
        try {
            if (isIgnorable(file)) {
                // System.out.println("File " + file + " is ignored");
                return null;
            } else {
                System.out.println("------------> Base path " + basePath);
                System.out.println("------------> Processing file " + file);

                Path filePath = Paths.get(file);
                System.out.println("Stream filePath: " + filePath.toAbsolutePath());

                File initialFile = new File(filePath.toAbsolutePath().toString().trim());
                InputStream stream = new FileInputStream(initialFile);

                String result = extractContentUsingParser(stream);

                index.setType("report");
                index.setContent(result);

                String filePathStr = file.replace(basePath, "");
                index.setFilePath(filePathStr);
                String frontendPath = filePathStr.replace("/" + organization.getSluglifiedName() + "/"
                        + team.getSluglifiedName() + "/reports/" + report.getSluglifiedName() + "/" + version + "/",
                        "");
                String link = "/" + organization.getSluglifiedName() + "/" + team.getSluglifiedName() + "/"
                        + report.getSluglifiedName() + "?path=" + frontendPath + "&version=" + version;
                index.setLink(link);

                System.out.println("------------> filePathStr " + filePathStr);
                System.out.println("------------> link " + link);

                int intVersion = -1;
                try {
                    intVersion = Integer.parseInt(version);
                } catch (Exception ex) {
                    // silent
                }
                index.setVersion(intVersion);

                index.setOrganizationSlug(organization.getSluglifiedName());
                index.setTeamSlug(team.getSluglifiedName());
                index.setEntityId(report.getId());
                index.setIsPublic(team.isPublic());

                String people = "";
                if (users.size() > 0) {
                    for (User user : users) {
                        people += user.getEmail() + " ";
                    }
                    people = people.substring(0, people.length() - 1);
                }
                index.setPeople(people);

                index.setTitle(report.getTitle());

                String tagsStr = "";
                if (tags.size() > 0) {
                    for (Tag tag : tags) {
                        tagsStr += tag.getName() + " ";
                    }
                    tagsStr = tagsStr.substring(0, tagsStr.length() - 1);
                }
                index.setTags(tagsStr);

            }
        } catch (TaggedIOException ex) {
            if (ex.getMessage().contains("a directory")) {
                // Is a directory, don't process it
            } else {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            // Do nothing, just skip that file
            ex.printStackTrace();
        }

        return index;
    }

    public static void deleteCurrentVersionIndex(KysoIndex data, String elasticUrl) {
        try {
            if (data == null) {
                System.out.println("Data is null. Skipping deleteCurrentVersionIndex");
                return;
            } else {
                System.out.println("Debugger: data");
                System.out.println("Version " + data.getVersion());
                System.out.println("Team " + data.getTeamSlug());
                System.out.println("Organization " + data.getOrganizationSlug());
                System.out.println("EntityId " + data.getEntityId());
            }

            if (elasticUrl == null) {
                System.out.println("elasticUrl is null. Skipping deleteCurrentVersionIndex");
                return;
            } else {
                System.out.println("Debugger: elasticUrl");
                System.out.println(elasticUrl);
            }

            String query = """
                        {
                            "query": {
                                "bool": {
                                    "must": [
                                        {
                                            "term": {
                                                "organizationSlug.keyword": "%%ORGANIZATION%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "teamSlug.keyword": "%%TEAM%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "entityId.keyword": "%%ENTITY%%"
                                            }
                                        },
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
                    .replace("%%VERSION%%", String.valueOf(data.getVersion()))
                    .replace("%%TEAM%%", data.getTeamSlug())
                    .replace("%%ORGANIZATION%%", data.getOrganizationSlug())
                    .replace("%%ENTITY%%", data.getEntityId());

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

            System.out.println(
                    "----------------------------> Deleted " + deleted + " older indexes for " + data.getFilePath());

        } catch (Exception ex) {
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
                                                "organizationSlug.keyword": "%%ORGANIZATION%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "teamSlug.keyword": "%%TEAM%%"
                                            }
                                        },
                                        {
                                            "term": {
                                                "entityId.keyword": "%%ENTITY%%"
                                            }
                                        },
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
                    .replace("%%VERSION%%", String.valueOf(data.getVersion()))
                    .replace("%%TEAM%%", data.getTeamSlug())
                    .replace("%%ORGANIZATION%%", data.getOrganizationSlug())
                    .replace("%%ENTITY%%", data.getEntityId());

            URI uri = new URI(elasticUrl + "/kyso-index/_search");
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Gson gson = new Gson();
            JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
            int results = responseBody.get("hits").getAsJsonObject().get("total").getAsJsonObject().get("value")
                    .getAsInt();

            if (results > 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            // System.out.println("--------------------> " + data.getLink() + " can't push
            // content to elasticsearch");
            // ex.printStackTrace();
            return false;
        }
    }

    public static void pushContentToElastic(KysoIndex data, String elasticUrl) {
        try {
            // Delete previous results for the same version, as we are going to save it
            // again anyways
            deleteCurrentVersionIndex(data, elasticUrl);

            Gson gson = new Gson();

            URI uri = new URI(elasticUrl + "/kyso-index/_doc?refresh=true");
            HttpClient client = HttpClient.newHttpClient();

            String indexAsJson = gson.toJson(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(indexAsJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("--------------------> " + data.getLink() + " upload to elastic returned: "
                    + response.statusCode());
        } catch (Exception ex) {
            System.out.println("--------------------> " + data.getLink() + " can't push content to elasticsearch");
            ex.printStackTrace();
        }
    }

    public static String extractContentUsingParser(InputStream stream) throws IOException, TikaException, SAXException {
        try {
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(stream, handler, metadata, context);
            String trimmedContent = handler.toString().trim().replaceAll("\\s+", " ");

            return trimmedContent;
        } catch (Exception ex) {
            System.out.println("Can't extract content from that input stream");
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            return "";
        }

    }
}