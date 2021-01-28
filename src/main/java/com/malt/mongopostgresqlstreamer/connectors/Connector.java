package com.malt.mongopostgresqlstreamer.connectors;

import java.util.stream.Stream;

import com.malt.mongopostgresqlstreamer.model.DatabaseMapping;
import com.malt.mongopostgresqlstreamer.model.FlattenMongoDocument;

public interface Connector {
	void createTable(String mappingName, DatabaseMapping mapping);

	void insert(String mappingName, FlattenMongoDocument document, DatabaseMapping mappings);

	void addConstraints(String mappingName, DatabaseMapping mapping);

	void upsert(String mappingName, FlattenMongoDocument document, DatabaseMapping mappings);

	void update(String mappingName, FlattenMongoDocument document, DatabaseMapping mappings);

	void remove(String mappingName, FlattenMongoDocument document, DatabaseMapping mappings);

	void bulkInsert(String mappingName, long totalNumberOfDocuments, Stream<FlattenMongoDocument> documents,
			DatabaseMapping mappings);
}
