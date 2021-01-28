package com.malt.mongopostgresqlstreamer.connectors.postgres.batch;

import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.text.StringEscapeUtils;
import org.postgresql.copy.CopyManager;

import com.malt.mongopostgresqlstreamer.connectors.postgres.Field;
import com.malt.mongopostgresqlstreamer.model.FieldMapping;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SingleTableCopyOperations {

	private final AtomicInteger valueCounter = new AtomicInteger();

	private final String table;
	private final String fieldsHeader;
	private final List<String> fieldNames;
	private final CopyManager copyManager;

	private StringBuilder copyString;

	SingleTableCopyOperations(String table, List<FieldMapping> fields, CopyManager copyManager) {
		this.table = table;
		this.fieldNames = fields.stream()
				.filter(mapping -> !mapping.isAnArray())
				.map(FieldMapping::getDestinationName)
				.sorted()
				.collect(toList());
		this.fieldsHeader = serialize(fieldNames);
		this.copyString = new StringBuilder(fieldsHeader);
		this.copyManager = copyManager;
	}

	void addOperation(List<Field> fields) {
		if (fields.size() < fieldNames.size()) {
			// Add missing field with NULL value
			log.trace("Expecting {} values but received {}. Adding NULL values to complete missing ones.",
					fieldNames.size(), fields.size());
			getMissingFields(fields).forEach(f -> fields.add(new Field(f, null)));
		} else if (fields.size() > fieldNames.size()) {
			throw new InvalidTableFieldException(table, fieldNames, fields);
		}

		copyString.append(serialize(fields.stream()
				.sorted()
				.map(Field::getValue)
				.collect(toList())));

		valueCounter.incrementAndGet();
	}

	private List<String> getMissingFields(List<Field> fields) {
		List<String> missingFields = new ArrayList<>();
		fieldNames.forEach(fieldName -> {
			if (!fields.stream()
					.anyMatch(f -> f.getName()
							.equals(fieldName))) {
				missingFields.add(fieldName);
			}
		});

		return missingFields;
	}

	private String serialize(List<?> parameters) {
		return String.join(",", parameters.stream()
				.map(this::serializeObject)
				.collect(toList())) + "\n";
	}

	private String serializeObject(Object parameter) {
		// TODO: Complete with other types ?

		if (parameter == null) {
			return "null";
		}

		if (parameter instanceof Boolean) {
			return (boolean) parameter ? "TRUE" : "FALSE";
		}

		// For unknown reason, the Mongo driver serialize some Int32 values
		// into BigDecimal.
		// Obviously, COPY expect a int (and not a double) which means
		// I have to check for this corner case
		if (parameter instanceof BigDecimal) {
			if (((BigDecimal) parameter).intValue() == ((BigDecimal) parameter).doubleValue()) {
				return String.valueOf(((BigDecimal) parameter).intValueExact());
			}
		}

		return StringEscapeUtils.escapeCsv(parameter.toString()
				// PostgreSql does not like UTF8 NULL byte or
				.replace("\u0000", ""));
	}

	private void releaseValues() {
		if (valueCounter.get() != 0) {
			commitPendingUpserts(this);
			this.copyString = new StringBuilder(fieldsHeader);
		}

		valueCounter.set(0);
	}

	String getTable() {
		return table;
	}

	String getCopyContent() {
		return copyString.toString();
	}

	InputStream getCopyContentStream() {
		return new ByteArrayInputStream(getCopyContent().getBytes());
	}

	private void commitPendingUpserts(SingleTableCopyOperations singleTableCopyOperations) {
		try {
			log.trace("COPY on {} : {}", singleTableCopyOperations.getTable(),
					singleTableCopyOperations.getCopyContent());
			copyManager.copyIn(
					"COPY " + singleTableCopyOperations.getTable()
							+ " FROM STDIN WITH DELIMITER ',' NULL as 'null' CSV HEADER",
					singleTableCopyOperations.getCopyContentStream());
		} catch (Exception e) {
			log.error("Unable to copy data for collection {}. Header was {}", singleTableCopyOperations.table,
					singleTableCopyOperations.fieldsHeader, e);
			throw new IllegalStateException(e);
		}
	}

	void finalizeOperations() {
		releaseValues();
	}

	int countPendingValues() {
		return valueCounter.get();
	}
}
