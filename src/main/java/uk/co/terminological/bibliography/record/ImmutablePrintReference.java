package uk.co.terminological.bibliography.record;

import java.io.Serializable;
import java.util.Optional;

public class ImmutablePrintReference implements Serializable, PrintReference {

	String firstname;
	String journal;
	String volume;
	String issue;
	Long year;
	String page;
	
	public ImmutablePrintReference(PrintReference p) {
		this.firstname = p.getFirstAuthorName().orElse(null);
		this.journal = p.getJournal().orElse(null);
		this.volume = p.getVolume().orElse(null);
		this.issue = p.getIssue().orElse(null);
		this.year = p.getYear().orElse(null);
		this.page = p.getPage().orElse(null);
	}
	
	@Override
	public Optional<String> getFirstAuthorName() {
		return Optional.ofNullable(firstname);
	}

	@Override
	public Optional<String> getJournal() {
		return Optional.ofNullable(journal);
	}

	@Override
	public Optional<String> getVolume() {
		return Optional.ofNullable(volume);
	}

	@Override
	public Optional<String> getIssue() {
		return Optional.ofNullable(issue);
	}

	@Override
	public Optional<Long> getYear() {
		return Optional.ofNullable(year);
	}

	@Override
	public Optional<String> getPage() {
		return Optional.ofNullable(page);
	}

}
