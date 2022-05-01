package io.kyso.api;

import io.kyso.App;
import io.quarkus.logging.Log;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.nio.file.Files;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;

import java.util.Date;

@Path("/api")
public class IndexerApi {
    public static final String FILE_PATH = "/indexer-tmp";
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/hello")
    public String hello() {
        return "Hello from RESTEasy Reactive";
    }


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/index")
    public String index(@QueryParam("elasticUrl") String elasticUrl,
                        @QueryParam("pathToIndex") String pathToIndex) {
        try {
            // Save file to process later
            Date currentDate = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
            String fileName = format.format(currentDate);
            fileName = fileName + ".indexer";

            Files.writeString(Paths.get(FILE_PATH, fileName), elasticUrl + "###" + pathToIndex, StandardOpenOption.CREATE_NEW);
            return "";
        } catch(Exception ex) {
            Log.error("Error", ex);
            throw new InternalServerErrorException(ex.getMessage());
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/reindex")
    public String reindex(@QueryParam("elasticUrl") String elasticUrl,
                        @QueryParam("pathToIndex") String pathToIndex) {
        try {
            String[] args = {elasticUrl, pathToIndex, "--reindex"};

            App.main(args);

            return "";
        } catch(Exception ex) {
            Log.error("Error", ex);
            throw new InternalServerErrorException(ex.getMessage());
        }
    }
}