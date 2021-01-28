package com.malt.mongopostgresqlstreamer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.malt.mongopostgresqlstreamer.model.DatabaseMapping;
import com.malt.mongopostgresqlstreamer.model.FieldMapping;
import com.malt.mongopostgresqlstreamer.model.FilterMapping;
import com.malt.mongopostgresqlstreamer.model.Mappings;
import com.malt.mongopostgresqlstreamer.model.TableMapping;

@Service
public class MappingsManager {

	private final ResourceResolverService resourceResolverService;
	private final String mappingFile;

	Mappings mappingConfigs;

	@Inject
	public MappingsManager(ResourceResolverService resourceResolverService,
			@Value("${mappings:mappings.json}") String mappingFile) {

		this.mappingFile = mappingFile;
		this.resourceResolverService = resourceResolverService;
	}

	@PostConstruct
	public void read() {
		mappingConfigs = read(mappingFile);
	}

	Mappings read(String mappingFile) {
		Mappings mappingConfigs = new Mappings();
		List<DatabaseMapping> dbs = new ArrayList<>();

		InputStream is = resourceResolverService.find(mappingFile);
		JsonObject mappings = new JsonParser().parse(new InputStreamReader(is))
				.getAsJsonObject();

		Set<String> databases = mappings.keySet();
		for (String dbName : databases) {
			DatabaseMapping db = readDatabaseMapping(mappings, dbName);
			dbs.add(db);
		}

		mappingConfigs.setDatabaseMappings(dbs);

		checkIntegrity(mappingConfigs);

		return mappingConfigs;
	}

	private DatabaseMapping readDatabaseMapping(JsonObject mappings, String dbName) {
		List<TableMapping> tableMappings = new ArrayList<>();
		DatabaseMapping db = new DatabaseMapping();
		db.setTableMappings(tableMappings);
		db.setName(dbName);

		JsonObject database = mappings.getAsJsonObject(dbName);
		for (String mappingName : database.keySet()) {
			TableMapping tableMapping = readTableMapping(database, mappingName);
			tableMappings.add(tableMapping);
		}
		return db;
	}

	private TableMapping readTableMapping(JsonObject database, String mappingName) {
		List<FieldMapping> fieldMappings = new ArrayList<>();
		List<String> indices = new ArrayList<>();
		List<FilterMapping> filters = new ArrayList<>();
		TableMapping tableMapping = new TableMapping();
		tableMapping.setIndices(indices);
		tableMapping.setMappingName(mappingName);
		tableMapping.setFilters(filters);
		// Default values
		tableMapping.setSourceCollection(mappingName);
		tableMapping.setDestinationName(mappingName);

		tableMapping.setFieldMappings(fieldMappings);
		JsonObject collection = database.getAsJsonObject(mappingName);
		if (collection.get("_source") != null) {
			tableMapping.setSourceCollection(collection.get("_source")
					.getAsString());
		}
		if (collection.get("_destination") != null) {
			tableMapping.setDestinationName(collection.get("_destination")
					.getAsString());
		}
		tableMapping.setPrimaryKey(collection.get("pk")
				.getAsString());

		addIndices(indices, collection);
		addCreationDateGeneratedFieldDefinition(tableMapping.getDestinationName(), fieldMappings, indices);

		for (String fieldName : collection.keySet()) {
			if (!fieldName.equals("pk") && !fieldName.equals("indices") && !fieldName.equals("_source")
					&& !fieldName.equals("_destination") && !fieldName.equals("_filters")) {
				FieldMapping fieldMapping = readFieldMapping(tableMapping.getDestinationName(), indices, collection,
						fieldName);
				fieldMappings.add(fieldMapping);
			}
		}
		if (!tableMapping.getByDestinationName(tableMapping.getPrimaryKey())
				.isPresent()) {
			tableMapping.getFieldMappings()
					.add(new FieldMapping("", "id", "VARCHAR", true, null, null));
		}

		JsonArray filtersMapping = collection.getAsJsonArray("_filters");
		Optional.ofNullable(filtersMapping)
				.ifPresent(f -> {
					for (JsonElement element : filtersMapping) {
						JsonObject filter = element.getAsJsonObject();
						filters.add(new FilterMapping(filter.get("field")
								.getAsString(),
								filter.get("value")
										.getAsString()));
					}
				});
		return tableMapping;
	}

	private void addIndices(List<String> indices, JsonObject collection) {
		if (collection.has("indices")) {
			JsonArray listOfIndices = collection.get("indices")
					.getAsJsonArray();
			for (JsonElement index : listOfIndices) {
				indices.add(index.getAsString());
			}
		}
	}

	private FieldMapping readFieldMapping(String tableName, List<String> indices, JsonObject collection,
			String fieldName) {
		JsonObject fieldObject = collection.getAsJsonObject(fieldName);
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setSourceName(fieldName);

		if (fieldObject.has("dest")) {
			fieldMapping.setDestinationName(fieldObject.get("dest")
					.getAsString());
		} else {
			fieldMapping.setDestinationName(fieldMapping.getSourceName());
		}

		fieldMapping.setType(fieldObject.get("type")
				.getAsString());

		if (fieldObject.has("index")) {
			fieldMapping.setIndexed(fieldObject.get("index")
					.getAsBoolean());
			addToIndices(tableName, indices, fieldMapping);
		}

		if (fieldObject.has("fk")) {
			fieldMapping.setForeignKey(fieldObject.get("fk")
					.getAsString());
		}

		if (fieldObject.has("valueField")) {
			fieldMapping.setScalarFieldDestinationName(fieldObject.get("valueField")
					.getAsString());
		}
		return fieldMapping;
	}

	private void checkIntegrity(Mappings mappingConfigs) {
		checkNoCollectionNameConflicts(mappingConfigs);
	}

	private void checkNoCollectionNameConflicts(Mappings mappingConfigs) {
		List<String> collectionNames = mappingConfigs.getDatabaseMappings()
				.stream()
				.flatMap(d -> d.getTableMappings()
						.stream())
				.map(TableMapping::getDestinationName)
				.collect(Collectors.toList());
		Set<String> duplicates = collectionNames.stream()
				.filter(s -> Collections.frequency(collectionNames, s) > 1)
				.collect(Collectors.toSet());
		if (duplicates.size() > 0) {
			throw new IllegalStateException(String.format("Your mappings have several tables with the same name. "
					+ "It will lead to conflicts in your database. The culprits are %s", duplicates));
		}

	}

	private void addToIndices(String tableName, List<String> indices, FieldMapping fieldMapping) {
		indices.add(String.format("INDEX idx_%s_%s ON %s (%s)", tableName.replace(".", "_"),
				fieldMapping.getDestinationName(), tableName, fieldMapping.getDestinationName()));
	}

	private void addCreationDateGeneratedFieldDefinition(String tableName, List<FieldMapping> fieldMappings,
			List<String> indices) {
		FieldMapping creationDateDefinition = new FieldMapping();
		creationDateDefinition.setType("TIMESTAMP");
		creationDateDefinition.setDestinationName("_creationdate");
		creationDateDefinition.setIndexed(true);
		creationDateDefinition.setSourceName("_creationdate");
		fieldMappings.add(creationDateDefinition);
		addToIndices(tableName, indices, creationDateDefinition);
	}

	public List<String> mappedNamespaces() {
		List<String> namespaces = new ArrayList<>();
		for (DatabaseMapping db : mappingConfigs.getDatabaseMappings()) {
			for (TableMapping tableMapping : db.getTableMappings()) {
				String namespace = db.getName() + "." + tableMapping.getSourceCollection();
				namespaces.add(namespace);
			}
		}
		return namespaces;
	}
}
