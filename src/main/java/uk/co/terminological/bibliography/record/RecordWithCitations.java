package uk.co.terminological.bibliography.record;

import java.util.Optional;
import java.util.stream.Stream;

public interface RecordWithCitations extends Record {

	public Stream<? extends CitationLink> getCitations();
	public Optional<Long> getCitedByCount();
	public Optional<Long> getReferencesCount();
	 		
}
