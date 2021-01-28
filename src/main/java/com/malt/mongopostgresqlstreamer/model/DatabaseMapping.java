package com.malt.mongopostgresqlstreamer.model;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Data;

@Data
public class DatabaseMapping {

	private String name;

	private List<TableMapping> tableMappings = new ArrayList<>();

	public Optional<TableMapping> get(String mappingName) {
		return tableMappings.stream()
				.filter(tableMapping -> tableMapping.getMappingName()
						.equals(mappingName))
				.findFirst();
	}

	public List<TableMapping> getBySourceName(String sourceName) {
		return tableMappings.stream()
				.filter(tableMapping -> tableMapping.getSourceCollection()
						.equals(sourceName))
				.collect(toList());
	}
}
