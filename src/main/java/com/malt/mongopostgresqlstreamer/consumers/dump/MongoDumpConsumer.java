package com.malt.mongopostgresqlstreamer.consumers.dump;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.malt.mongopostgresqlstreamer.consumers.ETLConsumer;
import com.malt.mongopostgresqlstreamer.consumers.dump.model.GenericCollectionRecord;
import com.malt.mongopostgresqlstreamer.consumers.dump.model.Header;
import com.malt.mongopostgresqlstreamer.consumers.dump.model.MongoCollection;
import com.malt.mongopostgresqlstreamer.mappings.MappingsManager;
import com.malt.mongopostgresqlstreamer.model.DatabaseMapping;
import com.malt.mongopostgresqlstreamer.model.FlattenMongoDocument;

import de.undercouch.bson4jackson.BsonFactory;
import de.undercouch.bson4jackson.BsonParser;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MongoDumpConsumer implements ETLConsumer {
	@Value("${mongo.dumpFolder:notset}")
	private File mongoDumpFolder;

	@Autowired
	private MappingsManager mappingsManager;

	private Map<String, DatabaseMapping> databaseMappings;

	@Override
	public void consume() {
		buildDatabaseMappingMap();

		if (mongoDumpFolder == null) {
			throw new RuntimeException("Mongodump input folder not set");
		}
		if (!mongoDumpFolder.exists()) {
			log.error("Mongodump input folder does not exists: " + mongoDumpFolder.getAbsolutePath());
		}
		if (!mongoDumpFolder.isDirectory()) {
			throw new RuntimeException("Mongodump input folder is not a folder");
		}

		List<File> dumps = Arrays.stream(mongoDumpFolder.listFiles())
				.filter(File::isFile)
				.filter(f -> f.getName()
						.endsWith(".gz"))
				.collect(Collectors.toList());

		dumps.forEach(this::consumeDump);
	}

	private void readMagic(DataInputStream in) throws IOException {
		byte[] magic = new byte[] { (byte) 0x6d, (byte) 0xe2, (byte) 0x99, (byte) 0x81 };
		byte[] value = in.readNBytes(4);
		if (!Arrays.equals(magic, value))
			throw new IOException("Wrong Magic header");

	}

	private void buildDatabaseMappingMap() {
		databaseMappings = new HashMap<>();
		mappingsManager.getMappingConfigs()
				.getDatabaseMappings()
				.forEach(dm -> {
					databaseMappings.put(dm.getName(), dm);
				});
	}

	private void consumeDump(File dumpFile) {
		log.info("Importing dump " + dumpFile.getAbsolutePath());

		BsonFactory jsonFactory = new BsonFactory();
		jsonFactory.enable(BsonParser.Feature.HONOR_DOCUMENT_LENGTH);
		SimpleModule simpleModule = new SimpleModule();

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(simpleModule);
		jsonFactory.setCodec(objectMapper);
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(dumpFile)))) {
			readMagic(in);
			readPrelude(jsonFactory, in);
			readBody(jsonFactory, in);
		} catch (Exception e) {
			log.error("Unexpected error", e);
		}

		log.info("Dump imported " + dumpFile.getAbsolutePath());
	}

	private void readPrelude(BsonFactory jsonFactory, DataInputStream in)
			throws IOException, JsonParseException, JsonMappingException {
		readObject(jsonFactory, in, Header.class);
		List<MongoCollection> collections = new ArrayList<>();

		for (;;) {
			try {
				MongoCollection collection = readObject(jsonFactory, in, MongoCollection.class);
				collections.add(collection);
				log.info("Collection " + collections.size() + ": " + collection.database + "/" + collection.name);
			} catch (EOFException e) {
				break;
			}
		}
	}

	private void readBody(BsonFactory jsonFactory, DataInputStream in) throws IOException {
		for (;;) {
			try {
				readSlice(jsonFactory, in);
			} catch (IOException e) {
				if (in.available() == 0) {
					break; // End of the entire dump
				} else {
					log.error("Unexpected error", e);
					break;
				}
			}
		}
	}

	private void readSlice(BsonFactory jsonFactory, DataInputStream in)
			throws IOException, JsonParseException, JsonMappingException {
		MongoCollection collection = readObject(jsonFactory, in, MongoCollection.class);
		if (!databaseMappings.containsKey(collection.database)) {
			skipCollectionSlice(jsonFactory, in, collection);
		} else {
			consumeCollectionSlice(jsonFactory, in, collection);
		}

	}

	private void consumeCollectionSlice(BsonFactory jsonFactory, DataInputStream in, MongoCollection collection)
			throws JsonParseException, JsonMappingException, IOException {
		String sliceName = collection.database + "/" + collection.name;
		log.info("Slice " + sliceName);
		long recordCount = 0;
		for (;;) {
			try {
				readFlattenMongoDocument(jsonFactory, in);
				recordCount++;
			} catch (EOFException e) {
				break;
			}
		}
		log.info("Slice " + sliceName + ": " + recordCount + " records consumed");

	}

	private void skipCollectionSlice(BsonFactory jsonFactory, DataInputStream in, MongoCollection collection)
			throws IOException, JsonParseException, JsonMappingException {
		String sliceName = collection.database + "/" + collection.name;
		log.info("Slice " + sliceName);
		long recordCount = 0;
		for (;;) {
			try {
				readObject(jsonFactory, in, GenericCollectionRecord.class);
				recordCount++;
			} catch (EOFException e) {
				break;
			}
		}
		log.info("Slice " + sliceName + ": " + recordCount + " records skipped");
	}

	private <E> E readObject(BsonFactory jsonFactory, DataInputStream in, Class<E> clazz)
			throws IOException, JsonParseException, JsonMappingException {
		try (JsonParser jp = jsonFactory.createJsonParser(in)) {
			return new ObjectMapper().readValue(jp, clazz);
		}
	}

	private FlattenMongoDocument readFlattenMongoDocument(BsonFactory jsonFactory, DataInputStream in)
			throws IOException, JsonParseException, JsonMappingException {
		try (JsonParser jp = jsonFactory.createJsonParser(in)) {
			FlatBsonDecoder d = new FlatBsonDecoder(jp);
			return FlattenMongoDocument.fromMap(d.decode());
		}
	}

}
