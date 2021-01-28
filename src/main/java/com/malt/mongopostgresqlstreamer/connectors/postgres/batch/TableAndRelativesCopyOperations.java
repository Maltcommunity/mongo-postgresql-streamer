package com.malt.mongopostgresqlstreamer.connectors.postgres.batch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.postgresql.copy.CopyManager;

import com.malt.mongopostgresqlstreamer.connectors.postgres.Field;
import com.malt.mongopostgresqlstreamer.model.FieldMapping;

class TableAndRelativesCopyOperations {

	private static final int CHUNK_SIZE = 500;

	private final CopyManager copyManager;
	private final Map<String, SingleTableCopyOperations> operationsForMainTableAndRelativesIncreasingDepthOfRelation = new LinkedHashMap<>();

	TableAndRelativesCopyOperations(CopyManager copyManager) {
		this.copyManager = copyManager;
	}

	void addOperation(String tableName, List<FieldMapping> fieldMappings, List<Field> fields) {
		SingleTableCopyOperations tableOperations = operationsForMainTableAndRelativesIncreasingDepthOfRelation
				.computeIfAbsent(tableName, tn -> new SingleTableCopyOperations(tn, fieldMappings, copyManager));

		tableOperations.addOperation(fields);

		if (countPendingValues() >= CHUNK_SIZE) {
			finalizeOperations();
		}
	}

	private int countPendingValues() {
		return operationsForMainTableAndRelativesIncreasingDepthOfRelation.values()
				.stream()
				.mapToInt(SingleTableCopyOperations::countPendingValues)
				.sum();
	}

	void finalizeOperations() {
		operationsForMainTableAndRelativesIncreasingDepthOfRelation.values()
				.forEach(SingleTableCopyOperations::finalizeOperations);
	}
}
