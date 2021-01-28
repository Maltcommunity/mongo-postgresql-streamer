package com.malt.mongopostgresqlstreamer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMapping {

	private String sourceName;
	private String destinationName;

	private String type;
	private boolean indexed;

	private String foreignKey;

	private String scalarFieldDestinationName;

	public boolean isAnArray() {
		return type.startsWith("_ARRAY");
	}

	public String getType() {
		if (type.equalsIgnoreCase("_PRESENCE")) {
			return "BOOLEAN DEFAULT FALSE";
		}
		return type;
	}

	public String getTrueType() {
		return type;
	}
}
