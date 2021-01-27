package com.malt.mongopostgresqlstreamer;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.BsonTimestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class StreamerApplication implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(StreamerApplication.class, args);
	}

	@Value("${mongo.connector.forcereimport:false}")
	private boolean forceReimport;

	@Value("${mongo.connector.stopAfterInitialImport:false}")
	private boolean stopAfterInitialImport;

	@Autowired
	private OplogStreamer oplogStreamer;
	@Autowired
	private InitialImporter initialImporter;
	@Autowired
	private CheckpointManager checkpointManager;

	@Value("${mongo.dumpFolder:notset}")
	private File mongoDumpFolder;

	@Override
	public void run(ApplicationArguments args) {
		if (mongoDumpFolder != null && !mongoDumpFolder.getName()
				.equals("notset")) {
			mongoDumpImport(mongoDumpFolder);
		} else {
			mongoDBImport();
		}

	}

	private void mongoDumpImport(File inputFolder) {
		if (inputFolder == null) {
			throw new RuntimeException("Mongodump input folder not set");
		}
		if (!inputFolder.exists()) {
			log.error("Mongodump input folder does not exists: " + inputFolder.getAbsolutePath());
		}
		List<File> dumps = Arrays.stream(inputFolder.listFiles())
				.filter(File::isFile)
				.filter(f -> f.getName()
						.endsWith(".gz"))
				.collect(Collectors.toList());

	}

	private void mongoDBImport() {
		Optional<BsonTimestamp> checkpoint = checkpointManager.getLastKnown();
		boolean checkpointPresent = checkpoint.isPresent();
		boolean performInitialLoad = !checkpointPresent || forceReimport;

		if (performInitialLoad) {
			if (!checkpointPresent) {
				log.info("No checkpoint found, we will perform a initial load");
			}

			if (forceReimport) {
				log.info("Forcing reimport, we will perform a initial load");
			}

			checkpoint = Optional.of(checkpointManager.getLastOplog());

			log.info("Last oplog found have timestamp : {}", checkpoint.get()
					.toString());
			checkpointManager.storeImportStart();

			long start = System.currentTimeMillis();
			initialImporter.start();
			long end = System.currentTimeMillis();

			long length = end - start;
			checkpointManager.keep(checkpoint.get());
			checkpointManager.storeImportEnd(length);

			if (stopAfterInitialImport) {
				log.info("Stopping after first end of import as requested");
				System.exit(0);
				return;
			}
		}

		try {
			oplogStreamer.watchFromCheckpoint(checkpoint);
		} catch (IllegalStateException e) {
			// "state should be: open" is thrown when the application is stopped and the
			// connection pool stops
			// this is not an error however
			if (!e.getMessage()
					.contains("state should be: open")) {
				throw e;
			}
		}
	}

}
