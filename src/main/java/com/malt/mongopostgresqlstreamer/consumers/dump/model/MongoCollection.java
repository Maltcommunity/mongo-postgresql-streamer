package com.malt.mongopostgresqlstreamer.consumers.dump.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MongoCollection {
	@JsonProperty("collection")
	public String name;
	@JsonProperty("db")
	public String database;
	public String metadata;
	public Long size;
}
