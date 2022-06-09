package io.kyso.api;

import io.kyso.App;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.kyso.api.TaskSchedulerBean.getFolderSize;

@Path("/api")
public class IndexerApi {
    @ConfigProperty(name = "app.indexer.filepath", defaultValue = "/indexer-tmp")
    String filePath;

    @ConfigProperty(name = "app.indexer.elasticsearch", defaultValue = "http://localhost:9200")
    String elasticsearchUrl;

    @ConfigProperty(name = "app.indexer.scsBasePath", defaultValue = "/data")
    String scsBasePath;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/index")
    public String index(@QueryParam("pathToIndex") String pathToIndex) {
        try {
            // Save file to process later
            Date currentDate = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
            String fileName = format.format(currentDate);
            fileName = fileName + ".indexer";

            Files.writeString(
                Paths.get(this.filePath, fileName),
                this.elasticsearchUrl + "###" + this.scsBasePath + "/" + pathToIndex,
                StandardOpenOption.CREATE_NEW
            );

            return "queued";
        } catch(Exception ex) {
            Log.error("Error", ex);
            throw new InternalServerErrorException(ex.getMessage());
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/reindex")
    public String reindex(@QueryParam("pathToIndex") String pathToIndex) {
        try {
            String[] args = {this.elasticsearchUrl, pathToIndex, "--reindex"};

            App.main(args);

            return "queued";
        } catch(Exception ex) {
            Log.error("Error", ex);
            throw new InternalServerErrorException(ex.getMessage());
        }
    }

    @GET
    @Path("/storage")
    @Produces(MediaType.APPLICATION_JSON)
    public OrganizationStorage getOrganizationStorage(@QueryParam("organizationFolderPath") String organizationFolderPath) {
        File organizationDirectory = new File(organizationFolderPath);
        if (!organizationDirectory.exists()) {
            throw new NotFoundException("Organization directory not found");
        }

        String[] directories = organizationDirectory.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        OrganizationStorage os = new OrganizationStorage();
        List<Storage> teamsStorage = new ArrayList<Storage>();
        double accumBytes = 0;
        for (String directory : directories) {
            Storage teamStorage = new Storage();
            teamStorage.setName(directory);
            File file = new File(organizationDirectory.getPath() + "/" + directory);
            long bytes = getFolderSize(file);
            accumBytes += bytes;
            teamStorage.setConsumedSpaceKb((double)bytes / 1024);
            teamStorage.setConsumedSpaceMb((double)bytes / (1024 * 1024));
            teamStorage.setConsumedSpaceGb((double)bytes / (1024 * 1024 * 1024));
            teamsStorage.add(teamStorage);
        }
        os.setTeams(teamsStorage);
        os.setConsumedSpaceKb((double)accumBytes / 1024);
        os.setConsumedSpaceMb((double)accumBytes / (1024 * 1024));
        os.setConsumedSpaceGb((double)accumBytes / (1024 * 1024 * 1024));
        return os;
    }
}