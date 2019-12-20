package uk.co.terminological.bibliography.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordIdentifier;
import uk.co.terminological.bibliography.record.RecordReference;

public interface IdLocator {

	Map<RecordIdentifier, ? extends Record> getById(Collection<RecordReference> equivalentIds);
	
	default Optional<? extends Record> getById(IdType type, String id) {
		RecordIdentifier tmp = Builder.recordReference(type,id); 
		return Optional.of(getById(Collections.singleton(tmp)).get(tmp));
	};
	
	default Optional<? extends Record> getById(Record rec) {
		Set<RecordReference> tmp = new HashSet<>();
		tmp.add(rec);
		tmp.addAll(rec.getOtherIdentifiers());
		return getById(tmp).values().stream().findFirst();
	};
	
}
