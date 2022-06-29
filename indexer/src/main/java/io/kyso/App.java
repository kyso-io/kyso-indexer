package io.kyso;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Command to watch files and run the process
// fswatch -e ".*" -i ".*/[^.]*\\.indexer$" --event Created . | xargs -I '{}' java -jar target/kyso-indexer-jar-with-dependencies.jar http://localhost:9200 {}

public class App {
    public static void main(String[] args, String mongoDbUri) throws Exception {

        MongoDbClient mongoDbClient = new MongoDbClient(mongoDbUri);

        System.out.println("Elasticsearch url: " + args[0]);
        String mode = "index";

        String filePath = "";
        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("--reindex")) {
                mode = "reindex";
                filePath = args[1];
                System.out.println("Processing path: " + filePath);
            } else {
                filePath = args[1] + "/" + args[2];
                System.out.println("Processing file: " + filePath);
            }
        } else {
            return;
        }

        Path path = Paths.get(filePath);
        String elasticUrl = args[0];

        if (mode.equalsIgnoreCase("index")) {
            // Receive a folder as parameter. Look for every file in that folder
            List<Path> allFiles;

            try (Stream<Path> paths = Files.walk(path)) {
                allFiles = paths
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toList());
            }

            String[] fileSplitted = args[2].split("/");
            System.out.println(String.join(", ", fileSplitted));

            String organizationSlug = fileSplitted[0];
            String teamSlug = fileSplitted[1];
            String report = fileSplitted[2];
            String version = fileSplitted[4];

            Organization organization = mongoDbClient.getOrganizationByOrganizationSlug(organizationSlug);
            Team team = mongoDbClient.getTeamByOrganizationIdAndTeamSlug(organization.getId(), teamSlug);

            List<KysoIndex> bulkInsert = new ArrayList<>();

            Map<String, Object> kysoMap = Indexer.findKysoYamlOrJsonByPath(allFiles);

            for (Path file : allFiles) {
                try {
                    String fileAbsolutePath = file.toAbsolutePath().toString();
                    KysoIndex index = Indexer.processFile(args[1], fileAbsolutePath, organizationSlug, team, report, version,
                            kysoMap);

                    if (index != null) {
                        bulkInsert.add(index);
                    }

                } catch (Exception ex) {
                    System.out.println("Cant process file " + file);
                    ex.printStackTrace();
                }
            }

            KysoIndex metadataIndex = Indexer.buildMetadataIndex(organizationSlug, team, report, version, kysoMap);
            bulkInsert.add(metadataIndex);

            System.out.println("----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
            // Save into elastic
            for (KysoIndex item : bulkInsert) {
                Indexer.pushContentToElastic(item, elasticUrl);
            }

            // Delete folder - now is not needed
            // Indexer.deleteFolder(new File(args[1]), allFiles);
        } else {
            // reindex
            System.out.println("Reindexing");

            // First get all organizations folders, which are in the base of the received
            // path
            File[] allOrganizationsFolders = new File(args[1]).listFiles(File::isDirectory);

            // Process organizations
            for (File organizationFolder : allOrganizationsFolders) {
                String organizationSlug = organizationFolder.getName();
                Organization organization = mongoDbClient.getOrganizationByOrganizationSlug(organizationSlug);
                System.out.println("Processing organization " + organizationSlug);

                // Get all teams folders
                File[] allTeamsFolders = organizationFolder.listFiles(File::isDirectory);

                // Process team
                for (File teamFolder : allTeamsFolders) {
                    String teamSlug = teamFolder.getName();
                    Team team = mongoDbClient.getTeamByOrganizationIdAndTeamSlug(organization.getId(), teamSlug);
                    System.out.println("----> Processing team " + teamSlug);
                    String reportsAbsolutePath = teamFolder.getAbsolutePath() + "/reports";

                    // Get all the reports of the team
                    File[] allReportsFolders = new File(reportsAbsolutePath).listFiles(File::isDirectory);

                    for (File reportFolder : allReportsFolders) {
                        String reportSlug = reportFolder.getName();
                        System.out.println("--------> Processing report " + reportSlug);

                        // Get all versions of the report, and take the most newer (higher)
                        File[] allVersionsFolders = reportFolder.listFiles(File::isDirectory);

                        File maxVersion = Arrays.stream(allVersionsFolders).max(Comparator.comparing(File::getName))
                                .orElse(null);

                        if (maxVersion != null) {
                            System.out.println("------------> Newer version is: " + maxVersion.getName());

                            // Find kyso.yaml,yml,json
                            File[] allRegularFilesInRootReport = maxVersion.listFiles(File::isFile);

                            Map<String, Object> kysoMap = Indexer.findKysoYamlOrJson(allRegularFilesInRootReport);

                            // Process all the files, subfolders, subfiles... whatever and process them
                            List<Path> allFilesOfFolder = Files.walk(Paths.get(maxVersion.getAbsolutePath()))
                                    .filter(Files::isRegularFile)
                                    .toList();

                            List<KysoIndex> bulkInsert = new ArrayList<>();

                            for (Path file : allFilesOfFolder) {
                                try {
                                    String fileAbsolutePath = file.toFile().getAbsolutePath();
                                    KysoIndex index = Indexer.processFile(args[1], fileAbsolutePath, organizationSlug, team,
                                            reportSlug, maxVersion.getName(), kysoMap);

                                    if (index != null) {
                                        bulkInsert.add(index);
                                    }

                                } catch (Exception ex) {
                                    System.out.println("Cant process file " + file.toFile().getAbsolutePath());
                                    ex.printStackTrace();
                                }
                            }

                            // KysoIndex metadataIndex = Indexer.buildMetadataIndex(organizationSlug, team, reportSlug,
                            //         maxVersion.getName(), kysoMap);
                            // bulkInsert.add(metadataIndex);

                            // Save into elastic
                            System.out.println(
                                    "----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
                            for (KysoIndex item : bulkInsert) {
                                Indexer.pushContentToElastic(item, elasticUrl);
                            }
                        }
                    }
                }
            }
        }
        
        mongoDbClient.closeConnection();
    }

}
