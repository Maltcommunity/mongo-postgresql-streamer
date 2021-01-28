package com.malt.mongopostgresqlstreamer.consumers.db;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malt.mongopostgresqlstreamer.connectors.Connector;
import com.malt.mongopostgresqlstreamer.mappings.MappingsManager;
import com.malt.mongopostgresqlstreamer.model.DatabaseMapping;
import com.malt.mongopostgresqlstreamer.model.FilterMapping;
import com.malt.mongopostgresqlstreamer.model.FlattenMongoDocument;
import com.malt.mongopostgresqlstreamer.model.TableMapping;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InitialImporter {

	@Autowired
	private MappingsManager mappingsManager;
	@Autowired
	private MongoClient mongoClient;
	@Autowired
	private List<Connector> connectors;

	public void start() {
		createSchema();
		populateData();
		addConstraints();
	}

	private void populateData() {
		for (DatabaseMapping databaseMapping : mappingsManager.getMappingConfigs()
				.getDatabaseMappings()) {
			MongoDatabase mongoDatabase = mongoClient.getDatabase(databaseMapping.getName());
			List<String> collectionNames = toStream(mongoDatabase.listCollectionNames()
					.iterator()).collect(toList());

			for (Connector connector : connectors) {
				for (TableMapping tableMapping : databaseMapping.getTableMappings()) {
					boolean needToBeImported = collectionNames.stream()
							.anyMatch(collectionIsMapped(tableMapping));
					if (needToBeImported) {
						String collectionName = tableMapping.getSourceCollection();
						String mappingName = tableMapping.getMappingName();
						connector.bulkInsert(mappingName, mongoDatabase.getCollection(collectionName)
								.count(),
								toStream(mongoDatabase.getCollection(collectionName)
										.find()
										.noCursorTimeout(true)
										.iterator()).map(FlattenMongoDocument::fromDocument)
												.filter(Objects::nonNull)
												.filter(tableMapping.getFilters()
														.stream()
														.map(FilterMapping::apply)
														.reduce(Predicate::or)
														.orElse(t -> true)),
								databaseMapping);
					}
				}
			}
		}
	}

	@Transactional
	protected void createSchema() {
		for (DatabaseMapping databaseMapping : mappingsManager.getMappingConfigs()
				.getDatabaseMappings()) {
			MongoDatabase mongoDatabase = mongoClient.getDatabase(databaseMapping.getName());
			List<String> collectionNames = toStream(mongoDatabase.listCollectionNames()
					.iterator()).collect(toList());
			for (Connector connector : connectors) {
				for (TableMapping tableMapping : databaseMapping.getTableMappings()) {
					boolean needToBeImported = collectionNames.stream()
							.anyMatch(collectionIsMapped(tableMapping));
					if (needToBeImported) {
						connector.createTable(tableMapping.getMappingName(), databaseMapping);
					}
				}
			}
		}
	}

	@Transactional
	protected void addConstraints() {
		log.info("Add constraints");
		for (DatabaseMapping databaseMapping : mappingsManager.getMappingConfigs()
				.getDatabaseMappings()) {
			MongoDatabase mongoDatabase = mongoClient.getDatabase(databaseMapping.getName());
			List<String> collectionNames = toStream(mongoDatabase.listCollectionNames()
					.iterator()).collect(toList());
			for (Connector connector : connectors) {
				for (TableMapping tableMapping : databaseMapping.getTableMappings()) {
					boolean needToBeImported = collectionNames.stream()
							.anyMatch(collectionIsMapped(tableMapping));
					if (needToBeImported) {
						connector.addConstraints(tableMapping.getMappingName(), databaseMapping);
					}
				}
			}
		}
	}

	private Predicate<String> collectionIsMapped(TableMapping tableMapping) {
		return collectionName -> collectionName.equals(tableMapping.getSourceCollection());
	}

	private <T> Stream<T> toStream(MongoCursor<T> iterator) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
	}
}
