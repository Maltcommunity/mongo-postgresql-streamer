package com.malt.mongopostgresqlstreamer.monitoring;

import java.util.Date;

import org.bson.BsonTimestamp;

import lombok.Data;

@Data
class Lag {

	private final Date lastCheckpoint;
	private final Date now;
	private final long lagLength;

	Lag() {
		this.lastCheckpoint = null;
		this.now = new Date();
		this.lagLength = -1;
	}

	Lag(BsonTimestamp checkpoint) {
		lastCheckpoint = new Date(((long) checkpoint.getTime()) * 1000);
		now = new Date();
		lagLength = now.getTime() - checkpoint.getTime();
	}
}
