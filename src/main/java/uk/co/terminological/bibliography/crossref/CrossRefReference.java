package uk.co.terminological.bibliography.crossref;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import uk.co.terminological.bibliography.ExtensibleJson;
import uk.co.terminological.bibliography.record.Author;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Print;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordReference;

public class CrossRefReference extends ExtensibleJson implements RecordReference, Print, Record {
	
	public CrossRefReference(JsonNode node) { super(node); }
	
	public Optional<String> getIdentifier() {return this.asString("DOI");}
	public Optional<String> getTitle() {return this.asString("article-title");}
	public Optional<String> getFirstAuthorName() {return this.asString("author");}
	public Optional<String> getJournal() {return this.asString("journal-title");}
	public Optional<String> getVolume() {return this.asString("volume");}
	public Optional<String> getIssue() {return this.asString("issue");}
	public Optional<Long> getYear() {return this.asString("year").map(Long::parseLong);}
	public Optional<String> getPage() {return this.asString("first-page");}

	@Override
	public IdType getIdentifierType() {
		return this.asString("DOI").isPresent() ? IdType.DOI : IdType.MID;
	}

	@Override
	public List<RecordReference> getOtherIdentifiers() {
		return Collections.emptyList();
	}

	@Override
	public List<? extends Author> getAuthors() {
		return Collections.singletonList(new Author() {

			@Override
			public Optional<String> getORCID() {
				return Optional.empty();
			}

			@Override
			public Optional<String> getFirstName() {
				return Optional.empty();
			}

			@Override
			public String getLastName() {
				return getFirstAuthorName().orElse("Unknown");
			}

			@Override
			public Optional<String> getInitials() {
				return Optional.empty();
			}

			@Override
			public Stream<String> getAffiliations() {
				return Stream.empty();
			}
			
		});
	}

	@Override
	public Stream<String> getLicenses() {
		return Stream.empty();
	}

	@Override
	public Optional<String> getAbstract() {
		return Optional.empty();
	}

	@Override
	public Optional<LocalDate> getDate() {
		return getYear().map(y -> LocalDate.of(y.intValue(), 1, 1));
	}

	@Override
	public Optional<URI> getPdfUri() {
		return Optional.empty();
	}
	
	
	/*@JsonProperty("key") public Optional<String> key = Optional.empty(); // Yes-
	@JsonProperty("DOI") public Optional<String> DOI = Optional.empty(); // No-
	@JsonProperty("doi-asserted-by") public Optional<String> doiAssertedBy = Optional.empty(); // No-One of crossref or publisher
	@JsonProperty("issue") public Optional<String> issue = Optional.empty(); // No-
	@JsonProperty("first-page") public Optional<String> firstPage = Optional.empty(); // No-
	@JsonProperty("volume") public Optional<String> volume = Optional.empty(); // No-
	@JsonProperty("edition") public Optional<String> edition = Optional.empty(); // No-
	@JsonProperty("component") public Optional<String> component = Optional.empty(); // No-
	@JsonProperty("standard-designator") public Optional<String> standardDesignator = Optional.empty(); // No-
	@JsonProperty("standards-body") public Optional<String> standardsBody = Optional.empty(); // No-
	@JsonProperty("author") public Optional<String> author = Optional.empty(); // No-
	@JsonProperty("year") public Optional<String> year = Optional.empty(); // No-
	@JsonProperty("unstructured") public Optional<String> unstructured = Optional.empty(); // No-
	@JsonProperty("journal-title") public Optional<String> journalTitle = Optional.empty(); // No-
	@JsonProperty("article-title") public Optional<String> articleTitle = Optional.empty(); // No-
	@JsonProperty("series-title") public Optional<String> seriesTitle = Optional.empty(); // No-
	@JsonProperty("volume-title") public Optional<String> volumeTitle = Optional.empty(); // No-
	@JsonProperty("ISSN") public Optional<String> ISSN = Optional.empty(); // No-
	@JsonProperty("issn-type") public Optional<String> issnType = Optional.empty(); // No-One of pissn or eissn
	@JsonProperty("ISBN") public Optional<String> ISBN = Optional.empty(); // No-
	@JsonProperty("isbn-type") public Optional<String> isbnType = Optional.empty(); // No-*/
}