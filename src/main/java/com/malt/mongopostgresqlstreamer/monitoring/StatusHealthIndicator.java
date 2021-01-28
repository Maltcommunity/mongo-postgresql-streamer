package com.malt.mongopostgresqlstreamer.monitoring;

import java.util.Optional;

import org.bson.BsonTimestamp;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.malt.mongopostgresqlstreamer.consumers.db.CheckpointManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

@Component
public class StatusHealthIndicator implements HealthIndicator {

	private final CheckpointManager checkpointManager;
	private final MongoDatabase oplog;

	StatusHealthIndicator(CheckpointManager checkpointManager, @Qualifier("oplog") MongoDatabase oplog) {

		this.checkpointManager = checkpointManager;
		this.oplog = oplog;
	}

	@Override
	public Health health() {
		Optional<BsonTimestamp> lastKnown = checkpointManager.getLastKnown();

		Lag lag = lastKnown.map(Lag::new)
				.orElseGet(Lag::new);
		boolean checkpointUp = lastKnown.map(this::checkIfCheckpointIsInOpLog)
				.orElse(false);
		InitialImport initialImport = checkpointManager.lastImportStatus();

		return Health.up()
				.withDetail("lag", lag)
				.withDetail("initial", initialImport)
				.withDetail("checkpoint", checkpointUp)
				.build();
	}

	private boolean checkIfCheckpointIsInOpLog(BsonTimestamp ts) {
		MongoCollection<Document> oplogCollection = this.oplog.getCollection("oplog.rs");
		Document firstOpLog = oplogCollection.find()
				.limit(1)
				.first();
		if (firstOpLog == null) {
			return false;
		}

		BsonTimestamp olderTs = firstOpLog.get("ts", BsonTimestamp.class);
		if (olderTs == null) {
			return false;
		}

		return olderTs.compareTo(ts) <= 0;
	}
}