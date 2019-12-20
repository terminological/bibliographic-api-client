package uk.co.terminological.bibliography.record;

import java.util.Optional;
import java.util.stream.Stream;

public interface Author {

	Optional<String> getORCID();
	Optional<String> getFirstName();
	String getLastName();
	Optional<String> getInitials();
	Stream<String> getAffiliations();
	
	
	public default String getLabel() {
		return (getLastName()+", "+getInitials().orElse("Unknown").substring(0, 1)).toLowerCase();
	}
}
