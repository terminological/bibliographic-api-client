package uk.co.terminological.bibliography.record;

import java.util.Optional;

public interface CitationLink {
	CitationReference getSource();
	CitationReference getTarget();
	Optional<Integer> getIndex();
	
	public default boolean isComplete() {
		return getSource().isComplete() && getTarget().isComplete();
	}
	
	static String print(CitationLink cl) {
		return cl
			.getIndex()
			.map(s -> s+") ")
			.orElse("")+
		cl.getSource().getIdentifier().map(RecordReference::print).orElse("Unknown source") + 
		" > " +
		cl.getTarget().getIdentifier().map(RecordReference::print).orElse("Unknown target");
	}
}
