package io.kyso.api;

import io.kyso.App;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.nio.file.Files;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;

import java.util.Date;

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
}