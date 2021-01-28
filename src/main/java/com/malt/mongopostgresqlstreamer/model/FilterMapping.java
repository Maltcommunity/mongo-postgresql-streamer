package com.malt.mongopostgresqlstreamer.model;

import java.util.function.Predicate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilterMapping implements Predicate<FlattenMongoDocument> {
	private String field;
	private String value;

	public static Predicate<FlattenMongoDocument> apply(FilterMapping filter) {
		return p -> p.get(filter.getField())
				.isPresent()
				&& p.get(filter.getField())
						.get()
						.equals(filter.getValue());
	}

	@Override
	public boolean test(FlattenMongoDocument flattenMongoDocument) {
		return flattenMongoDocument.get(this.field)
				.isPresent()
				&& flattenMongoDocument.get(this.field)
						.get() == value;
	}
}
