package com.malt.mongopostgresqlstreamer.model;

import com.eclipsesource.json.ParseException;
import com.github.wnameless.json.flattener.FlattenMode;
import com.github.wnameless.json.flattener.JsonFlattener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.json.JsonParseException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Data
public class FlattenMongoDocument {
    private Map<String, Object> values;

    public static FlattenMongoDocument fromMap(Map map) {
        map = filters(map);
        FlattenMongoDocument flattenMongoDocument = new FlattenMongoDocument();
        flattenMongoDocument.setValues(map);
        addCreationDateIfPossible(flattenMongoDocument);
        fixDateOutOfRange(flattenMongoDocument);

        return flattenMongoDocument;
    }


    public static FlattenMongoDocument fromDocument(Document document) {
        FlattenMongoDocument flattenMongoDocument = new FlattenMongoDocument();

        try {
            flattenMongoDocument.setValues(
                    filters(
                            new JsonFlattener(document.toJson())
                                    .withFlattenMode(FlattenMode.KEEP_ARRAYS)
                                    .flattenAsMap()
                    )
            );
            addCreationDateIfPossible(flattenMongoDocument);
            fixDateOutOfRange(flattenMongoDocument);
        } catch(JsonParseException ex) {
            log.error("Could not parse JSON of document: {}.", document.toString());
            throw ex;
        } catch(ParseException ex) {
            log.error("Could not parse JSON of document: {}.", document.toString());
            throw ex;
        }

        return flattenMongoDocument;
    }

    private static Map<String, Object> filters(Map<String, Object> flattenAsMap) {
        Map<String, Object> filteredMap = new HashMap<>();
        flattenAsMap.forEach((k, v) -> {
            if (k.contains(".$oid")) {
                k = k.replace(".$oid", "");
            } else if (k.contains(".$date")) {
                k = k.replace(".$date", "");
                v = new Date(((Number) v).longValue());
            } else if (k.contains(".$numberLong")) {
                k = k.replace(".$numberLong", "");
                v = Long.parseLong((String) v);
            } else if (k.contains(".$numberDecimal")) {
                k = k.replace(".$numberDecimal", "");
                v = new BigDecimal((String) v);
            }
            filteredMap.put(k.replace(".$oid", ""), v);
        });

        return filteredMap;
    }

    private static void addCreationDateIfPossible(FlattenMongoDocument flattenMongoDocument) {
        flattenMongoDocument.get("_id").ifPresent( id -> {
            if (id instanceof ObjectId) {
                Date creationDate = ((ObjectId) id).getDate();
                flattenMongoDocument.getValues().put("_creationdate", creationDate);
            } else if (id instanceof String && ObjectId.isValid((String) id)) {
                Date creationDate = ((new ObjectId((String) id))).getDate();
                flattenMongoDocument.getValues().put("_creationdate", creationDate);
            }
        });
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(values.getOrDefault(key, null));
    }

    public FlattenMongoDocument withField(String key, Object value) {
        this.values.put(key, value);
        return this;
    }


    private static void fixDateOutOfRange(FlattenMongoDocument flattenMongoDocument) {
        Map<String, Object> values = flattenMongoDocument.getValues();
        values.forEach((k, v) -> {
            if (v instanceof Date) {
                Date date = (Date) v;
                if ((date.getYear()+1900) < 1900 || (date.getYear()+1900 > 2050) ) {
                    values.put(k, null);
                }
            }
        });
    }
}
