package com.malt.mongopostgresqlstreamer.connectors.postgres.batch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Service;

import com.malt.mongopostgresqlstreamer.connectors.postgres.Field;
import com.malt.mongopostgresqlstreamer.model.FieldMapping;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CopyOperationsManager {
	private final CopyManager copyManager;
	private final Map<String, TableAndRelativesCopyOperations> copyOperationsPerTable = new ConcurrentHashMap<>(1);

	@Inject
	public CopyOperationsManager(CopyManager copyManager) {
		this.copyManager = copyManager;
	}

	public void addInsertOperation(String parentTable, String table, List<FieldMapping> fieldMappings,
			List<Field> fields) {
		TableAndRelativesCopyOperations operations = copyOperationsPerTable.computeIfAbsent(parentTable,
				notFound -> new TableAndRelativesCopyOperations(copyManager));
		operations.addOperation(table, fieldMappings, fields);
	}

	public void finalizeCopyOperations(String destParentTable) {
		TableAndRelativesCopyOperations operations = copyOperationsPerTable.get(destParentTable);
		if (operations != null) {
			operations.finalizeOperations();
		}
	}
}
