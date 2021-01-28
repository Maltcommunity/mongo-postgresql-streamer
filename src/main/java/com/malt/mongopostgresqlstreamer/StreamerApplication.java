package com.malt.mongopostgresqlstreamer;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.malt.mongopostgresqlstreamer.consumers.db.MongoDbConsumer;
import com.malt.mongopostgresqlstreamer.consumers.dump.MongoDumpConsumer;

@SpringBootApplication
public class StreamerApplication implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(StreamerApplication.class, args);
	}

	@Autowired
	private MongoDbConsumer mongoDbConsumer;

	@Autowired
	private MongoDumpConsumer mongoDumpConsumer;

	@Value("${mongo.dumpFolder:notset}")
	private File mongoDumpFolder;

	@Override
	public void run(ApplicationArguments args) {
		if (mongoDumpFolder != null && !mongoDumpFolder.getName()
				.equals("notset")) {
			mongoDumpConsumer.consume();
		} else {
			mongoDbConsumer.consume();
		}

	}
}
