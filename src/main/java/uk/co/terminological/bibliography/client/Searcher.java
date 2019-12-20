package uk.co.terminological.bibliography.client;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

import uk.co.terminological.bibliography.record.Record;

public interface Searcher {

	Collection<? extends Record> search(String search, Optional<LocalDate> from,  Optional<LocalDate> to, Optional<Integer> limit);
	
	default Collection<? extends Record> search(String search) {
		return search(search, Optional.empty(), Optional.empty(), Optional.empty());
	};
	
	default Collection<? extends Record> search(String search, Integer limit) {
		return search(search, Optional.empty(), Optional.empty(), Optional.of(limit));
	};
	
	default Collection<? extends Record> search(String search, LocalDate from, LocalDate to) {
		return search(search, Optional.of(from), Optional.of(to), Optional.empty());
	};
	
	
	
}
