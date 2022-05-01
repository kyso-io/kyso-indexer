package io.kyso.api;

import io.kyso.App;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;

import javax.enterprise.context.ApplicationScoped;

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

    @Scheduled(every = "1s")
    void indexPending() {
        if(this.isExecuting.get()) {
            Log.warn("Process is being executed");
        }

        this.isExecuting.set(true);

        try {
            List<String> files = findFiles(Paths.get(IndexerApi.FILE_PATH), "indexer");

            for(String file : files) {
                try {
                    Log.info("Processing file " + file);

                    String contentFile = Files.readString(Paths.get(file));

                    String[] args = contentFile.split("###");

                    App.main(args);

                    Files.delete(Paths.get(file));
                } catch(Exception ex) {
                    Log.error("Error processing file " + file, ex);
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
}