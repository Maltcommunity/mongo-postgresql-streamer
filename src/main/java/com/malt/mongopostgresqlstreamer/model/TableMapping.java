package com.malt.mongopostgresqlstreamer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Data;

@Data
public class TableMapping {
	private String mappingName;
	private String sourceCollection;
	private String destinationName;

	private String primaryKey;
	private List<FieldMapping> fieldMappings = new ArrayList<>();
	private List<String> indices = new ArrayList<>();
	private List<FilterMapping> filters = new ArrayList<>();

	public Optional<FieldMapping> getByDestinationName(String destinationFieldName) {
		return fieldMappings.stream()
				.filter(field -> field.getDestinationName()
						.equals(destinationFieldName))
				.findFirst();
	}

	public Optional<FieldMapping> getBySourceName(String sourceFieldName) {
		return fieldMappings.stream()
				.filter(field -> field.getSourceName()
						.equals(sourceFieldName))
				.findFirst();
	}

	public boolean isMapped(String sourceFieldName) {
		return getBySourceName(sourceFieldName).isPresent();
	}

	public List<FieldMapping> getArrayFieldMappings() {
		return fieldMappings.stream()
				.filter(field -> field.getType()
						.startsWith("_ARRAY"))
				.collect(Collectors.toList());
	}
}
