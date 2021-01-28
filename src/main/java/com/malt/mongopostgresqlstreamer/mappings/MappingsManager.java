package com.malt.mongopostgresqlstreamer.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.malt.mongopostgresqlstreamer.resources.ResourceResolverService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MappingsManager {

	private final ResourceResolverService resourceResolverService;
	private final File mapping;

	private Mappings mappingConfigs;

	public Mappings getMappingConfigs() {
		return mappingConfigs;
	}

	@Inject
	public MappingsManager(ResourceResolverService resourceResolverService,
			@Value("${mappings:mappings.json}") String mappingPath) {

		this.mapping = new File(mappingPath);

		this.resourceResolverService = resourceResolverService;
	}

	@PostConstruct
	public void read() {
		mappingConfigs = read(mapping);
	}

	public Mappings read(File mapping) {
		Mappings mappingConfigs = new Mappings();
		List<DatabaseMapping> dbs = new ArrayList<>();

		if (mapping.isFile()) {
			collectDatabaseMappings(true, mapping, dbs);
		} else if (mapping.isDirectory()) {
			for (File mapFile : Arrays.stream(mapping.listFiles())
					.filter(File::isFile)
					.filter(f -> f.getName()
							.endsWith(".json"))
					.collect(Collectors.toList())) {
				collectDatabaseMappings(false, mapFile, dbs);

			}
		}

		mappingConfigs.setDatabaseMappings(dbs);

		checkIntegrity(mappingConfigs);

		return mappingConfigs;
	}

	private void collectDatabaseMappings(boolean aggregate, File mapping, List<DatabaseMapping> dbs) {
		log.info("Import database mapping " + mapping.getAbsolutePath() + "...");
		try {

			String inputJson = loadJson(mapping);
			if (!aggregate) {
				inputJson = injectDbName(mapping, inputJson);
			}
			JsonObject mappings = new JsonParser().parse(inputJson)
					.getAsJsonObject();

			Set<String> databases = mappings.keySet();
			for (String dbName : databases) {
				DatabaseMapping db = readDatabaseMapping(mappings, dbName);
				dbs.add(db);
			}
		} catch (Exception e) {
			log.error("Import failed for database mapping " + mapping.getAbsolutePath(), e);
		}
	}

	private String injectDbName(File mapping, String inputJson) {
		String dbName = mapping.getName();
		int idx = dbName.lastIndexOf(".json");
		if (idx == -1)
			throw new RuntimeException("Database mapping filename should ends with .json");
		dbName = dbName.substring(0, idx);
		inputJson = "{\"" + dbName + "\":" + inputJson + "\n}";
		return inputJson;
	}

	private String loadJson(File mapping) {
		InputStream is = resourceResolverService.find(mapping.getAbsolutePath());
		String inputJson = new BufferedReader(new InputStreamReader(is)).lines()
				.collect(Collectors.joining("\n"));
		return inputJson;
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
