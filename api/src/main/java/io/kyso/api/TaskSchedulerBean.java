package io.kyso.api;

import io.kyso.App;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class TaskSchedulerBean {
    private AtomicBoolean isExecuting = new AtomicBoolean(false);

    @ConfigProperty(name = "app.indexer.filepath", defaultValue = "/indexer-data")
    private String filePath;


    @Scheduled(cron = "{cron.expr}")
    void indexPending() {
        Log.info("Starting indexing process");

        if(this.isExecuting.get()) {
            Log.warn("Process is being executed");
            return;
        }

        this.isExecuting.set(true);

        try {
            List<String> files = findFiles(Paths.get(this.filePath), "indexer");

            Log.info("There are " + files.size() + " files pending to process");

            for(String file : files) {
                try {
                    Log.info("Processing file " + file);

                    String contentFile = Files.readString(Paths.get(file));

                    String[] args = contentFile.split("###");

                    App.main(args);

                    Files.delete(Paths.get(file));
                } catch(Exception ex) {
                    Log.error("Error processingF file " + file, ex);
                }
            }

            this.isExecuting.set(false);
        } catch(Exception ex) {
            Log.error("Error procession index pending", ex);
            this.isExecuting.set(false);
        } finally {
            this.isExecuting.set(false);
        }
    }

    public static List<String> findFiles(Path path, String fileExtension)
            throws IOException {

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path must be a directory!");
        }

        List<String> result;

        try (Stream<Path> walk = Files.walk(path)) {
            result = walk
                    .filter(p -> !Files.isDirectory(p))
                    // this is a path, not string,
                    // this only test if path end with a certain path
                    //.filter(p -> p.endsWith(fileExtension))
                    // convert path to string first
                    .map(p -> p.toString().toLowerCase())
                    .filter(f -> f.endsWith(fileExtension))
                    .collect(Collectors.toList());
        }

        return result;
    }

    public static long getFolderSize(File folder) {
        File[] files = folder.listFiles();
        long length = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            } else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }
}