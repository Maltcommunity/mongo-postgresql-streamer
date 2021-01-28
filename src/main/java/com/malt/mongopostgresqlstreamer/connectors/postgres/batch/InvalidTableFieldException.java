package com.malt.mongopostgresqlstreamer.connectors.postgres.batch;

import java.util.Collection;
import java.util.stream.Collectors;

import com.malt.mongopostgresqlstreamer.connectors.postgres.Field;

class InvalidTableFieldException extends RuntimeException {

	private static final String DELIMITER = ", ";

	public InvalidTableFieldException(String tableName, Collection<String> expectedFields,
			Collection<Field> actualFields) {
		super(createMessage(tableName, expectedFields, actualFields));
	}

	private static String createMessage(String tableName, Collection<String> expectedFields,
			Collection<Field> actualFields) {
		int nbExpectedFields = expectedFields.size();
		int nbActualFields = actualFields.size();

		String expectedFieldNames = String.join(", ", expectedFields);
		String actualFieldNames = actualFields.stream()
				.map(Field::getName)
				.collect(Collectors.joining(DELIMITER));
		return "Operations to table '" + tableName + "' " + "expects " + nbExpectedFields + " values ("
				+ expectedFieldNames + ") " + "but received " + nbActualFields + " (" + actualFieldNames + ")";
	}
}
