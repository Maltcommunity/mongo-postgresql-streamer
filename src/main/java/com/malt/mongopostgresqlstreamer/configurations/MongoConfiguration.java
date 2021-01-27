package com.malt.mongopostgresqlstreamer.configurations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;

@Configuration
public class MongoConfiguration {

	@Value(value = "${mongo.uri:mongodb://localhost:27017}")
	private String uri;

	@Value(value = "${mongo.admin.database:test}")
	private String dbName;

	@Bean
	public MongoClient client() {
		return new MongoClient(new MongoClientURI(uri));
	}

	@Bean
	@Qualifier("database")
	public MongoDatabase database(MongoClient client) {
		return client.getDatabase(dbName);
	}

	@Bean
	@Qualifier("oplog")
	public MongoDatabase oplog(MongoClient client) {
		return client.getDatabase("local");
	}
}
