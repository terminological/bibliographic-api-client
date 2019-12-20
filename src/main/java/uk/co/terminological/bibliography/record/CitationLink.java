package uk.co.terminological.bibliography.record;

import java.util.Optional;

public interface CitationLink {
	CitationReference getSource();
	CitationReference getTarget();
	Optional<Integer> getIndex();
}
