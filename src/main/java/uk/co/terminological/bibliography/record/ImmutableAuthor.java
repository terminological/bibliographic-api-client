package uk.co.terminological.bibliography.record;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImmutableAuthor implements Author, Serializable {
	
	private String orcid;
	private String firstName;
	private String lastName;
	private String initials;
	private Collection<String> affiliations;

	public ImmutableAuthor(Author author) {
		this.orcid = author.getORCID().orElse(null);
		this.firstName = author.getFirstName().orElse(null);
		this.lastName = author.getLastName();
		this.initials = author.getInitials().orElse(null);
		this.affiliations = author.getAffiliations().collect(Collectors.toList());
	}
	
	@Override
	public Optional<String> getORCID() {
		return Optional.ofNullable(orcid);
	}

	@Override
	public Optional<String> getFirstName() {
		return Optional.ofNullable(firstName);
	}

	@Override
	public String getLastName() {
		return lastName;
	}

	@Override
	public Optional<String> getInitials() {
		return Optional.ofNullable(initials);
	}

	@Override
	public Stream<String> getAffiliations() {
		return affiliations.stream();
	}

}
