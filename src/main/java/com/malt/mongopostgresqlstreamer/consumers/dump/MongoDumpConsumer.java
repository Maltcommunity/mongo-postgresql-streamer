package com.malt.mongopostgresqlstreamer.consumers.dump;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.malt.mongopostgresqlstreamer.consumers.ETLConsumer;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MongoDumpConsumer implements ETLConsumer {
	@Value("${mongo.dumpFolder:notset}")
	private File mongoDumpFolder;

	@Override
	public void consume() {
		if (mongoDumpFolder == null) {
			throw new RuntimeException("Mongodump input folder not set");
		}
		if (!mongoDumpFolder.exists()) {
			log.error("Mongodump input folder does not exists: " + mongoDumpFolder.getAbsolutePath());
		}
		List<File> dumps = Arrays.stream(mongoDumpFolder.listFiles())
				.filter(File::isFile)
				.filter(f -> f.getName()
						.endsWith(".gz"))
				.collect(Collectors.toList());
		
		throw new NotImplementedException("Mongodump import not supported");

	}

}
