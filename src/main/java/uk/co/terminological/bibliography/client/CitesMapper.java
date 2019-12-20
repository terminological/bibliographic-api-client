package uk.co.terminological.bibliography.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.RecordReference;

public interface CitesMapper {
	
	public Set<? extends CitationLink> citesReferences(Collection<RecordReference> ref);
	
	default Set<? extends CitationLink> citesReferences(RecordReference ref) {
		return citesReferences(Collections.singleton(ref));
	};
	
}
