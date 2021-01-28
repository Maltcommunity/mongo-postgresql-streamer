package com.malt.mongopostgresqlstreamer.connectors.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Field implements Comparable {
	private String name;
	private Object value;

	public boolean isList() {
		return value instanceof Iterable;
	}

	@Override
	public int compareTo(Object o) {
		if (o == null) {
			return 1;
		}

		if (o instanceof Field) {
			return this.name.compareTo(((Field) o).name);
		}

		return -1;
	}
}
