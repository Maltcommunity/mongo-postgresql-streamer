package com.malt.mongopostgresqlstreamer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.File;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.malt.mongopostgresqlstreamer.mappings.MappingsManager;
import com.malt.mongopostgresqlstreamer.model.DatabaseMapping;
import com.malt.mongopostgresqlstreamer.model.FieldMapping;
import com.malt.mongopostgresqlstreamer.model.Mappings;
import com.malt.mongopostgresqlstreamer.model.TableMapping;
import com.malt.mongopostgresqlstreamer.resources.ResourceResolverService;

class MappingsManagerTest {
	private static final String FILE_NAME = "mapping.json";

	private String filePath;
	private MappingsManager mappingsManager;

	@BeforeEach
	void setUp() {
		ResourceResolverService resourceResolverService = new ResourceResolverService(
				new DefaultResourceLoader(Thread.currentThread()
						.getContextClassLoader()));

		filePath = Objects.requireNonNull(this.getClass()
				.getClassLoader()
				.getResource(FILE_NAME))
				.getPath();
		mappingsManager = new MappingsManager(resourceResolverService, filePath);
	}

	@Test
	void should_parse_valid_mapping_file() {
		Mappings result = mappingsManager.read(new File(filePath));

		assertThat(result).isNotNull();

		List<DatabaseMapping> databaseMappings = result.getDatabaseMappings();
		assertThat(databaseMappings).isNotNull()
				.hasSize(1)
				.extracting("name")
				.containsExactly("my_mongo_database");

		List<TableMapping> tableMappings = databaseMappings.get(0)
				.getTableMappings();
		assertThat(tableMappings).isNotNull()
				.hasSize(3);

		TableMapping table1 = tableMappings.get(0);
		// When we don't specify any source or destination, we use the mapping name
		assertThat(table1.getMappingName()).isEqualTo("my_mongo_collection");
		assertThat(table1.getSourceCollection()).isEqualTo("my_mongo_collection");
		assertThat(table1.getDestinationName()).isEqualTo("my_mongo_collection");
		assertThat(table1.getFieldMappings()).isNotNull()
				.extracting("sourceName", "destinationName", "type")
				.containsExactly(tuple("_creationdate", "_creationdate", "TIMESTAMP"), tuple("_id", "id", "TEXT"));
		assertThat(table1.getFilters()).isEmpty();
		assertThat(table1.getIndices()).isNotEmpty();

		TableMapping table2 = tableMappings.get(1);
		assertThat(table2.getMappingName()).isEqualTo("custom_mapping_name");
		assertThat(table2.getSourceCollection()).isEqualTo("my_mongo_collection");
		assertThat(table2.getDestinationName()).isEqualTo("my_destination_table");
		assertThat(table2.getFieldMappings()).isNotNull()
				.extracting("sourceName", "destinationName", "type")
				.containsExactly(tuple("_creationdate", "_creationdate", "TIMESTAMP"), tuple("_id", "id", "TEXT"),
						tuple("description", "description", "TEXT"));
		assertThat(table2.getFilters()).isEmpty();

		TableMapping table3 = tableMappings.get(1);
		assertThat(table3.getMappingName()).isEqualTo("custom_mapping_name");
		assertThat(table3.getSourceCollection()).isEqualTo("my_mongo_collection");
		assertThat(table3.getDestinationName()).isEqualTo("my_destination_table");
		assertThat(table3.getFieldMappings()).isNotNull()
				.extracting("sourceName", "destinationName", "type")
				.containsExactly(tuple("_creationdate", "_creationdate", "TIMESTAMP"), tuple("_id", "id", "TEXT"),
						tuple("description", "description", "TEXT"));
		assertThat(table3.getFilters()).isEmpty();

	}

	@Test
	void it_should_read_mapping_with_table_association() throws Exception {
		String filePath = this.getClass()
				.getClassLoader()
				.getResource("mapping-with-related-table.json")
				.getPath();

		Mappings result = mappingsManager.read(new File(filePath));
		assertThat(result).isNotNull();

		List<DatabaseMapping> dbMappings = result.getDatabaseMappings();
		assertThat(dbMappings).hasSize(1);

		DatabaseMapping dbMapping = dbMappings.get(0);
		assertThat(dbMapping.getName()).isEqualTo("my_mongo_database");
		assertThat(dbMapping.getTableMappings()).hasSize(2);

		List<TableMapping> tableMappings = dbMapping.getTableMappings();

		TableMapping table1 = tableMappings.get(0);
		assertThat(table1.getSourceCollection()).isEqualTo("teams");
		assertThat(table1.getDestinationName()).isEqualTo("teams");
		assertThat(table1.getMappingName()).isEqualTo("teams");
		assertThat(table1.getPrimaryKey()).isEqualTo("id");
		assertThat(table1.getFilters()).isEmpty();
		assertThat(table1.getIndices()).hasSize(1)
				.contains("INDEX idx_teams__creationdate ON teams (_creationdate)");

		assertThat(table1.getFieldMappings()).hasSize(4)
				.extracting(FieldMapping::getSourceName, FieldMapping::getDestinationName, FieldMapping::getType,
						FieldMapping::getForeignKey, FieldMapping::isIndexed, FieldMapping::isAnArray,
						FieldMapping::getTrueType, FieldMapping::getScalarFieldDestinationName)
				.contains(tuple("_creationdate", "_creationdate", "TIMESTAMP", null, true, false, "TIMESTAMP", null),
						tuple("_id", "id", "TEXT", null, false, false, "TEXT", null),
						tuple("name", "name", "TEXT", null, false, false, "TEXT", null),
						tuple("members", "team_members", "_ARRAY", "team_id", false, true, "_ARRAY", null));

		TableMapping table2 = tableMappings.get(1);
		assertThat(table2.getSourceCollection()).isEqualTo("team_members");
		assertThat(table2.getDestinationName()).isEqualTo("team_members");
		assertThat(table2.getMappingName()).isEqualTo("team_members");
		assertThat(table2.getPrimaryKey()).isEqualTo("id");
		assertThat(table2.getFilters()).isEmpty();
		assertThat(table2.getIndices()).hasSize(2)
				.contains("INDEX idx_team_members__creationdate ON team_members (_creationdate)",
						"INDEX idx_team_members_team_id ON team_members (team_id)");

		assertThat(table2.getFieldMappings()).hasSize(4)
				.extracting(FieldMapping::getSourceName, FieldMapping::getDestinationName, FieldMapping::getType,
						FieldMapping::getForeignKey, FieldMapping::isIndexed, FieldMapping::isAnArray,
						FieldMapping::getTrueType, FieldMapping::getScalarFieldDestinationName)
				.contains(tuple("_creationdate", "_creationdate", "TIMESTAMP", null, true, false, "TIMESTAMP", null),
						tuple("team_id", "team_id", "TEXT", null, true, false, "TEXT", null),
						tuple("name", "name", "TEXT", null, false, false, "TEXT", null),
						tuple("", "id", "VARCHAR", null, true, false, "VARCHAR", null));
	}
}
