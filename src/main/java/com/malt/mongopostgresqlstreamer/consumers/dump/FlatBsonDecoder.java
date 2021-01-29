package com.malt.mongopostgresqlstreamer.consumers.dump;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.undercouch.bson4jackson.types.ObjectId;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlatBsonDecoder {
	private JsonParser jsonParser;
	private Map<String, Object> documentMap = new HashMap<>();

	public FlatBsonDecoder(JsonParser jsonParser) {
		this.jsonParser = jsonParser;
	}

	public Map<String, Object> decode() throws IOException {
		readValue("");
		// dumpDocumentMap();
		return documentMap;
	}

	private void readValue(String prefix) throws IOException {
		JsonToken jt = jsonParser.nextToken();
		consumeToken(prefix, jt);
	}

	private void consumeToken(String prefix, JsonToken jt) throws IOException {
		if (jt == JsonToken.START_OBJECT) {
			readObject(prefix);
		} else if (jt == JsonToken.START_ARRAY) {
			readArray(prefix);
		} else if (jt == JsonToken.VALUE_STRING) {
			documentMap.put(prefix, jsonParser.getValueAsString());
		} else if (jt == JsonToken.VALUE_NUMBER_INT) {
			documentMap.put(prefix, jsonParser.getValueAsLong());
		} else if (jt == JsonToken.VALUE_TRUE) {
			documentMap.put(prefix, jsonParser.getValueAsBoolean());
		} else if (jt == JsonToken.VALUE_FALSE) {
			documentMap.put(prefix, jsonParser.getValueAsBoolean());
		} else if (jt == JsonToken.VALUE_NULL) {
			documentMap.put(prefix, null);
		} else if (jt == JsonToken.VALUE_NUMBER_FLOAT) {
			documentMap.put(prefix, jsonParser.getValueAsDouble());
		} else if (jt == JsonToken.VALUE_EMBEDDED_OBJECT) {
			Object obj = jsonParser.getEmbeddedObject();
			if (obj instanceof ObjectId) {
				ObjectId oid = (ObjectId) obj;
				org.bson.types.ObjectId mongoObjectId = org.bson.types.ObjectId.createFromLegacyFormat(oid.getTime(),
						oid.getMachine(), oid.getInc());
				obj = mongoObjectId;
			}
			documentMap.put(prefix, obj);
		} else {
			log.error("Unexpected token " + jt.name() + " " + jt.asString());
		}
	}

	private void readObject(String prefix) throws IOException {
		while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldname = jsonParser.getCurrentName();
			if (prefix.length() == 0)
				readValue(fieldname);
			else
				readValue(prefix + "." + fieldname);
		}

	}

	private void readArray(String prefix) throws IOException {
		int idx = 0;
		for (;;) {
			JsonToken jt = jsonParser.nextToken();
			if (jt == JsonToken.END_ARRAY)
				break;
			consumeToken(prefix + "[" + idx + "]", jt);
			idx++;
		}

	}

	private void dumpDocumentMap() {
		documentMap.keySet()
				.stream()
				.sorted(String::compareTo)
				.forEach(key -> {
					log.info(key + ":" + documentMap.get(key));
				});
	}
}
