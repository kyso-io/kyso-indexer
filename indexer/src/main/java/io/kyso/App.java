package io.kyso;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Command to watch files and run the process
// fswatch -e ".*" -i ".*/[^.]*\\.indexer$" --event Created . | xargs -I '{}' java -jar target/kyso-indexer-jar-with-dependencies.jar http://localhost:9200 {}

public class App {
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
            // Receive a folder as parameter. Look for every file in that folder
            List<Path> allFiles;

            try (Stream<Path> paths = Files.walk(path)) {
                allFiles = paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
            }

            String[] fileSplitted = allFiles.get(0).toAbsolutePath().toString().split("/");
            System.out.println(fileSplitted);

            String organization = fileSplitted[4];
            String team = fileSplitted[5];
            String report = fileSplitted[7];
            String version = fileSplitted[8];

            List<KysoIndex> bulkInsert = new ArrayList<>();

            Map<String, Object> kysoMap = Indexer.findKysoYamlOrJsonByPath(allFiles);

            for(Path file : allFiles) {
                try {
                    String fileAbsolutePath = file.toAbsolutePath().toString();
                    KysoIndex index = Indexer.processFile(fileAbsolutePath, organization, team, report, version, kysoMap);

                    if(index != null) {
                        bulkInsert.add(index);
                    }

                } catch(Exception ex) {
                    System.out.println("Cant process file " + file);
                    ex.printStackTrace();
                }
            }

            KysoIndex metadataIndex = Indexer.buildMetadataIndex(organization, team, report, version, kysoMap);
            bulkInsert.add(metadataIndex);

            System.out.println("----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
            // Save into elastic
            bulkInsert.forEach(item -> Indexer.pushContentToElastic(item, elasticUrl));

            // Delete folder - now is not needed
            // Indexer.deleteFolder(new File(args[1]), allFiles);
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

                        if(maxVersion != null) {
                            System.out.println("------------> Newer version is: " + maxVersion.getName());

                            // Find kyso.yaml,yml,json
                            File[] allRegularFilesInRootReport = maxVersion.listFiles(File::isFile);

                            Map<String, Object> kysoMap = Indexer.findKysoYamlOrJson(allRegularFilesInRootReport);

                            // Process all the files, subfolders, subfiles... whatever and process them
                            List<Path> allFilesOfFolder = Files.walk(Paths.get(maxVersion.getAbsolutePath()))
                                    .filter(Files::isRegularFile)
                                    .toList();

                            List<KysoIndex> bulkInsert = new ArrayList<>();

                            for(Path file : allFilesOfFolder) {
                                try {
                                    String fileAbsolutePath = file.toFile().getAbsolutePath();
                                    KysoIndex index = Indexer.processFile(fileAbsolutePath, organizationSlug, teamSlug, reportSlug, maxVersion.getName(), kysoMap);

                                    if(index != null) {
                                        bulkInsert.add(index);
                                    }

                                } catch(Exception ex) {
                                    System.out.println("Cant process file " + file.toFile().getAbsolutePath());
                                    ex.printStackTrace();
                                }
                            }

                            KysoIndex metadataIndex = Indexer.buildMetadataIndex(organizationSlug, teamSlug, reportSlug, maxVersion.getName(), kysoMap);
                            bulkInsert.add(metadataIndex);

                            // Save into elastic
                            System.out.println("----------------> Uploading to Elastic " + bulkInsert.size() + " registries");
                            bulkInsert.forEach(item -> Indexer.pushContentToElastic(item, elasticUrl));
                        }
                    }
                }
            }
        }
    }

}
