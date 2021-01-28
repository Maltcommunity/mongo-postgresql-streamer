package com.malt.mongopostgresqlstreamer.model;

import java.util.List;
import java.util.Optional;

import lombok.Data;

@Data
public class Mappings {
	private List<DatabaseMapping> databaseMappings;

	public Optional<DatabaseMapping> databaseMappingFor(String databaseName) {
		return databaseMappings.stream()
				.filter(d -> d.getName()
						.equalsIgnoreCase(databaseName))
				.findFirst();
	}
}
