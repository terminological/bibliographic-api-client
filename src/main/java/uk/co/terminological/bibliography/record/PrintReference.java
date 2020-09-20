package uk.co.terminological.bibliography.record;

import java.util.Optional;

public interface PrintReference {

	public Optional<String> getFirstAuthorName();
	public Optional<String> getJournal();
	public Optional<String> getVolume();
	public Optional<String> getIssue();
	public Optional<Long> getYear();
	public Optional<String> getPage();
	
	
}
