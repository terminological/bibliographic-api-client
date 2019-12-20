package uk.co.terminological.bibliography.record;

import java.util.Optional;

public interface RecordReference {

	public Optional<String> getIdentifier();
	public IdType getIdentifierType();
	
	
}
