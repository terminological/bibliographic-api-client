package uk.co.terminological.bibliography.client;

import java.util.Collection;
import java.util.Set;

import uk.co.terminological.bibliography.record.RecordIdentifierMapping;
import uk.co.terminological.bibliography.record.RecordReference;

public interface IdMapper {

	public Set<? extends RecordIdentifierMapping> mappings(Collection<RecordReference> source);
}
