package com.malt.mongopostgresqlstreamer;

import lombok.extern.slf4j.Slf4j;
import org.bson.BsonTimestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

@SpringBootApplication
@Slf4j
@Profile("production")
public class StreamerApplication implements ApplicationRunner {

    public static void main(String[] args) {
        SpringApplication.run(StreamerApplication.class, args);
    }

    @Value("${mongo.connector.forcereimport:true}")
    private boolean forceReimport;

    @Autowired
    private OplogStreamer oplogStreamer;
    @Autowired
    private InitialImporter initialImporter;
    @Autowired
    private CheckpointManager checkpointManager;

    @Override
    public void run(ApplicationArguments args) {
        Optional<BsonTimestamp> checkpoint = checkpointManager.getLastKnown();

        if (!checkpoint.isPresent() || forceReimport) {
            log.info("No checkpoint found, we will perform a initial load");
            checkpoint = Optional.of(checkpointManager.getLastOplog());
            log.info("Last oplog found have timestamp : {}", checkpoint.get().toString());
            checkpointManager.storeImportStart();
            long start = System.currentTimeMillis();
            initialImporter.start();
            long end = System.currentTimeMillis();
            long length = end - start;
            checkpointManager.keep(checkpoint.get());
            checkpointManager.storeImportEnd(length);
        }

        try {
            oplogStreamer.watchFromCheckpoint(checkpoint);
        } catch (IllegalStateException e) {
            // state should be: open is throw when the application is stopped and the connection pool stop
            // this is not an error however
            if (!e.getMessage().contains("state should be: open")) {
                throw e;
            }
        }
    }

}
