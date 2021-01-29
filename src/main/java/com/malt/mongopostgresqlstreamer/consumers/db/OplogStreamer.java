package com.malt.mongopostgresqlstreamer.consumers.db;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malt.mongopostgresqlstreamer.connectors.Connector;
import com.malt.mongopostgresqlstreamer.mappings.MappingsManager;
import com.malt.mongopostgresqlstreamer.model.FilterMapping;
import com.malt.mongopostgresqlstreamer.model.FlattenMongoDocument;
import com.malt.mongopostgresqlstreamer.model.TableMapping;
import com.mongodb.CursorType;
import com.mongodb.MongoClient;
import com.mongodb.MongoQueryException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OplogStreamer {

	@Value(value = "${mongo.connector.identifier:streamer}")
	private String identifier;

	@Value(value = "${mongo.database:test}")
	private String dbName;

	@Autowired
	private MappingsManager mappingsManager;
	@Autowired
	private CheckpointManager checkpointManager;
	@Autowired
	@Qualifier("oplog")
	private MongoDatabase oplog;
	@Autowired
	private MongoClient mongoClient;
	@Autowired
	private List<Connector> connectors;

	public void watchFromCheckpoint(Optional<BsonTimestamp> checkpoint) {
		int watches = 1;

		while (true) {
			log.info("Start watching the oplog ({})...", watches++);

			try (MongoCursor<Document> documents = oplogDocuments(checkpoint).iterator()) {
				documents.forEachRemaining(document -> {
					BsonTimestamp timestamp = processOperation(document);
					checkpointManager.keep(timestamp);
				});
			} catch (MongoQueryException e) {
				String msg = e.getErrorMessage();
				if (msg.contains("CappedPositionLost") || msg.contains("MongoCursorNotFoundException")) {
					int numberOfSecondsToWait = 1;
					log.info("Cursor lost, retrying in {}s", numberOfSecondsToWait, e);

					// wait a bit so that we don't loop too fast in case that code generates an
					// infinite loop
					try {
						Thread.sleep(numberOfSecondsToWait * 1000);
					} catch (InterruptedException ex) {
						Thread.currentThread()
								.interrupt();
						return;
					}
				} else {
					throw e;
				}
			}
		}
	}

	private FindIterable<Document> oplogDocuments(Optional<BsonTimestamp> checkpoint) {
		MongoCollection<Document> oplog = this.oplog.getCollection("oplog.rs");
		if (checkpoint.isPresent()) {
			Document lastKnownOplog = oplog.find(eq("ts", checkpoint.get()))
					.first();
			if (lastKnownOplog == null) {
				log.error("Last known oplog is not in the oplog anymore. The watch will starts from first "
						+ "oplog but you should consider relaunch a reimport");
				checkpoint = Optional.empty();
			}
		}
		return oplog.find(oplogfilters(checkpoint))
				.cursorType(CursorType.TailableAwait)
				.noCursorTimeout(true);
	}

	@Transactional
	BsonTimestamp processOperation(Document document) {
		String namespace = document.getString("ns");
		String[] databaseAndCollection = namespace.split("\\.");
		String collection = databaseAndCollection[1];
		String database = databaseAndCollection[0];
		String operation = document.getString("op");
		BsonTimestamp timestamp = document.get("ts", BsonTimestamp.class);

		mappingsManager.getMappingConfigs()
				.databaseMappingFor(database)
				.ifPresent(mappings -> {
					MongoDatabase mongoDb = mongoClient.getDatabase(database);
					List<TableMapping> tableMappings = mappings.getBySourceName(collection);
					tableMappings.forEach(tableMapping -> {
						log.debug("Operation {} detected on {}", operation, namespace);
						Predicate<FlattenMongoDocument> mappingFilters = tableMapping.getFilters()
								.stream()
								.map(FilterMapping::apply)
								.reduce(Predicate::or)
								.orElse(x -> true);

						switch (operation) {
						case "i":
							FlattenMongoDocument newDocument = FlattenMongoDocument
									.fromDocument((Document) document.get("o"));
							if (mappingFilters.test(newDocument)) {
								connectors.forEach(connector -> connector.upsert(tableMapping.getMappingName(),
										newDocument, mappings));
							}
							break;
						case "u":
							Map documentIdToUpdate = (Map) document.get("o2");
							Document updatedDocument = mongoDb.getCollection(tableMapping.getSourceCollection())
									.find(eq("_id", documentIdToUpdate.get("_id")))
									.first();
							if (updatedDocument != null) {
								FlattenMongoDocument flattenMongoDocument = FlattenMongoDocument
										.fromDocument(updatedDocument);
								if (mappingFilters.test(flattenMongoDocument)) {
									connectors.forEach(connector -> connector.update(tableMapping.getMappingName(),
											flattenMongoDocument, mappings));
								}
							}
							break;
						case "d":
							Document documentIdToRemove = (Document) document.get("o");
							connectors.forEach(connector -> connector.remove(tableMapping.getMappingName(),
									FlattenMongoDocument.fromDocument(documentIdToRemove), mappings));
							break;
						default:
							break;
						}
					});

				});

		return timestamp;
	}

	private Bson oplogfilters(Optional<BsonTimestamp> checkpoint) {
		return checkpoint
				.map(bsonTimestamp -> and(in("ns", mappingsManager.mappedNamespaces()), gt("ts", bsonTimestamp),
						exists("fromMigrate", false), in("op", "d", "u", "i")))

				.orElseGet(() -> and(in("ns", mappingsManager.mappedNamespaces()), exists("fromMigrate", false),
						in("op", "d", "u", "i")));
	}

}
