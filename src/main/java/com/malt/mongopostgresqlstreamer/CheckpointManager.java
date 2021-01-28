package com.malt.mongopostgresqlstreamer;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

import java.util.Date;
import java.util.Optional;

import org.bson.BsonTimestamp;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.malt.mongopostgresqlstreamer.monitoring.InitialImport;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CheckpointManager {

	private static final String OPLOG_COLLECTION_NAME = "oplog.rs";

	private final MongoDatabase database;
	private final MongoDatabase oplog;
	private final String identifier;

	@Autowired
	CheckpointManager(@Qualifier("database") MongoDatabase database, @Qualifier("oplog") MongoDatabase oplog,
			@Value(value = "${mongo.connector.identifier:test}") String identifier) {

		this.database = database;
		this.oplog = oplog;
		this.identifier = identifier;
	}

	public Optional<BsonTimestamp> getLastKnown() {
		MongoCollection<Document> oplogOffset = database.getCollection("mongooplog");
		Document lastProcessedOplog = oplogOffset.find(eq("_id", identifier))
				.first();
		Optional<BsonTimestamp> checkpoint = Optional.empty();
		if (lastProcessedOplog != null && lastProcessedOplog.get("ts", BsonTimestamp.class) != null) {
			checkpoint = Optional.ofNullable(lastProcessedOplog.get("ts", BsonTimestamp.class));
			log.debug("Checkpoint found in the administrative database : {}.", checkpoint.get()
					.toString());
		}
		return checkpoint;
	}

	public InitialImport lastImportStatus() {
		MongoCollection<Document> collection = database.getCollection("mongooplog");
		Document status = collection.find(eq("_id", identifier))
				.first();
		Date start = status.getDate("start");
		Date end = status.getDate("end");
		String state = status.getString("import");
		Double length = status.getDouble("length");

		InitialImport initialImport = new InitialImport();
		initialImport.setStart(start);
		initialImport.setEnd(end);
		initialImport.setStatus(state);
		initialImport.setLengthInMinutes(length);
		return initialImport;
	}

	BsonTimestamp getLastOplog() {
		Document lastOplog = oplog.getCollection(OPLOG_COLLECTION_NAME)
				.find()
				.sort(Sorts.descending("$natural"))
				.first();

		if (lastOplog != null) {
			return lastOplog.get("ts", BsonTimestamp.class);
		}

		throw new IllegalStateException(
				"Unable to retrieve last oplog. Maybe you are not running your mongodb in a replica set");
	}

	void keep(BsonTimestamp timestamp) {
		MongoCollection<Document> collection = database.getCollection("mongooplog");
		collection.updateOne(eq("_id", identifier), set("ts", timestamp), new UpdateOptions().upsert(true));
	}

	void storeImportStart() {
		MongoCollection<Document> collection = database.getCollection("mongooplog");
		collection.updateOne(eq("_id", identifier),
				combine(set("import", "running"), set("start", new Date()), set("ts", null)),
				new UpdateOptions().upsert(true));
	}

	void storeImportEnd(float length) {
		float lenghtInMinutes = (length / 1000F) / 60F;

		MongoCollection<Document> collection = database.getCollection("mongooplog");
		collection.updateOne(eq("_id", identifier),
				combine(set("import", "done"), set("end", new Date()), set("length", lenghtInMinutes)),
				new UpdateOptions().upsert(true));

	}
}
