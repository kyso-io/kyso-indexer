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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Command to watch files and run the process
// fswatch -e ".*" -i ".*/[^.]*\\.indexer$" --event Created . | xargs -I '{}' java -jar target/kyso-indexer-jar-with-dependencies.jar http://localhost:9200 {}

public class App {
    public static void main(String[] args, String mongoDbUri) throws Exception {
        Random r = new Random(System.currentTimeMillis());
        int randomIdentifier = r.nextInt();

        System.out.println(String.format("[%d] Opening MongoDB connection", randomIdentifier));

        MongoDbClient mongoDbClient = new MongoDbClient(mongoDbUri);

        try {
            System.out.println("Elasticsearch url: " + args[0]);
            String mode = "index";

            String filePath;
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
                String reportSlug = fileSplitted[3];
                String version = fileSplitted[4];

                System.out.println("Indexing report: " + organizationSlug + " - " + teamSlug + " - " + reportSlug
                        + " - " + version);

                Organization organization = mongoDbClient.getOrganizationByOrganizationSlug(organizationSlug);
                if (organization == null) {
                    System.out.println("Organization not found");
                    return;
                }
                Team team = mongoDbClient.getTeamByOrganizationIdAndTeamSlug(organization.getId(), teamSlug);
                if (team == null) {
                    System.out.println("Team not found");
                    return;
                }
                Report report = mongoDbClient.getReportByTeamIdAndReportSlug(team.getId(), reportSlug);
                if (report == null) {
                    System.out.println("Report not found");
                    return;
                }
                ArrayList<User> users = new ArrayList<User>();
                for (String userId : report.getAuthorIds()) {
                    User author = mongoDbClient.getUserByUserId(userId);
                    users.add(author);
                }
                ArrayList<Tag> tags = mongoDbClient.getTagsGivenEntityId(report.getId());
                int stars = mongoDbClient.getNumberOfStarsGivenReportId(report.getId());
                int numComments = mongoDbClient.getNumberOfCommentsGivenReportId(report.getId());
                int numTasks = mongoDbClient.getNumberOfTaskGivenReportId(report.getId(), Integer.parseInt(version));
                long updatedAt = report.getUpdatedAt().getTime();

                List<KysoIndex> bulkInsert = new ArrayList<>();

                Map<String, Object> kysoMap = Indexer.findKysoYamlOrJsonByPath(allFiles);

                for (Path file : allFiles) {
                    try {
                        String fileAbsolutePath = file.toAbsolutePath().toString();
                        KysoIndex index = Indexer.processFile(args[1], fileAbsolutePath, organization, team, report,
                                version, users, tags, stars, numComments, numTasks, updatedAt, kysoMap);

                        if (index != null) {
                            bulkInsert.add(index);
                        }

                    } catch (Exception ex) {
                        System.out.println("Cant process file " + file);
                        ex.printStackTrace();
                    }
                }

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
                if (allOrganizationsFolders == null) {
                    System.out.println("No organization folders found in path: " + args[1] + ". Stopping process!");
                    mongoDbClient.closeConnection();
                    return;
                }

                // Process organizations
                for (File organizationFolder : allOrganizationsFolders) {
                    String organizationSlug = organizationFolder.getName();
                    Organization organization = mongoDbClient.getOrganizationByOrganizationSlug(organizationSlug);
                    if (organization == null) {
                        System.out.println("Organization not found: " + organizationSlug);
                        continue;
                    }
                    System.out.println("Processing organization: " + organization.getId() + " "
                            + organization.getSluglifiedName());

                    // Get all teams folders
                    File[] allTeamsFolders = organizationFolder.listFiles(File::isDirectory);

                    // Process team
                    for (File teamFolder : allTeamsFolders) {
                        try {
                            String teamSlug = teamFolder.getName();
                            Team team = mongoDbClient.getTeamByOrganizationIdAndTeamSlug(organization.getId(),
                                    teamSlug);
                            if (team == null) {
                                System.out.println("Team not found: " + teamSlug);
                                continue;
                            }
                            System.out.println("----> Processing team " + teamSlug);
                            String reportsAbsolutePath = teamFolder.getAbsolutePath() + "/reports";

                            // Get all the reports of the team
                            File[] allReportsFolders = new File(reportsAbsolutePath).listFiles(File::isDirectory);

                            for (File reportFolder : allReportsFolders) {
                                try {
                                    String reportSlug = reportFolder.getName();
                                    Report report = mongoDbClient.getReportByTeamIdAndReportSlug(team.getId(),
                                            reportSlug);
                                    if (report == null) {
                                        System.out.println("Report not found: " + reportSlug);
                                        continue;
                                    }
                                    int version = mongoDbClient.getLastVersionOfReport(report.getId());
                                    System.out.println(
                                            "--------> Processing report: " + report.getId() + " " + reportSlug);

                                    ArrayList<User> users = new ArrayList<User>();
                                    for (String userId : report.getAuthorIds()) {
                                        User author = mongoDbClient.getUserByUserId(userId);
                                        users.add(author);
                                    }

                                    ArrayList<Tag> tags = mongoDbClient.getTagsGivenEntityId(report.getId());
                                    int stars = mongoDbClient.getNumberOfStarsGivenReportId(report.getId());
                                    int numComments = mongoDbClient.getNumberOfCommentsGivenReportId(report.getId());
                                    int numTasks = mongoDbClient.getNumberOfTaskGivenReportId(report.getId(), version);

                                    long updatedAt = report.getUpdatedAt().getTime();

                                    // Get all versions of the report, and take the most newer (higher)
                                    File[] allVersionsFolders = reportFolder.listFiles(File::isDirectory);

                                    File maxVersion = Arrays.stream(allVersionsFolders)
                                            .max(Comparator.comparing(File::getName))
                                            .orElse(null);

                                    if (maxVersion != null) {
                                        System.out.println("------------> Newer version is: " + maxVersion.getName());

                                        // Find kyso.yaml,yml,json
                                        File[] allRegularFilesInRootReport = maxVersion.listFiles(File::isFile);

                                        Map<String, Object> kysoMap = Indexer
                                                .findKysoYamlOrJson(allRegularFilesInRootReport);

                                        // Process all the files, subfolders, subfiles... whatever and process them
                                        List<Path> allFilesOfFolder = Files
                                                .walk(Paths.get(maxVersion.getAbsolutePath()))
                                                .filter(Files::isRegularFile)
                                                .toList();

                                        List<KysoIndex> bulkInsert = new ArrayList<>();

                                        for (Path file : allFilesOfFolder) {
                                            try {
                                                String fileAbsolutePath = file.toFile().getAbsolutePath();
                                                KysoIndex index = Indexer.processFile(args[1], fileAbsolutePath,
                                                        organization, team, report, maxVersion.getName(), users, tags,
                                                        stars, numComments, numTasks, updatedAt, kysoMap);
                                                if (index != null) {
                                                    bulkInsert.add(index);
                                                }
                                            } catch (Exception ex) {
                                                System.out.println(
                                                        "Cant process file " + file.toFile().getAbsolutePath());
                                                ex.printStackTrace();
                                            }
                                        }

                                        // Save into elastic
                                        System.out.println(
                                                "----------------> Uploading to Elastic " + bulkInsert.size()
                                                        + " registries");
                                        for (KysoIndex item : bulkInsert) {
                                            try {
                                                Indexer.pushContentToElastic(item, elasticUrl);
                                            } catch (Exception ex) {
                                                System.out.println("Error saving indexed content into Elastic");
                                                ex.printStackTrace();
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    System.out.println("Error processing files (lvl:reportFolder)");
                                    ex.printStackTrace();
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error processing files (lvl:teamFolder)");
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Generic exception raised");
            ex.printStackTrace();
        } finally {
            System.out.println(String.format("[%d] Closing MongoDB connection", randomIdentifier));
            mongoDbClient.closeConnection();
        }
    }

}
