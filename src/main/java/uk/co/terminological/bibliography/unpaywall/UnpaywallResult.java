package uk.co.terminological.bibliography.unpaywall;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import uk.co.terminological.bibliography.ExtensibleJson;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordReference;

public class UnpaywallResult extends ExtensibleJson implements Record {
	public UnpaywallResult(JsonNode node) {super(node);}
	
	public Optional<String> getIdentifier() {return this.asString("doi");}
	public Optional<String> getTitle() {return Optional.ofNullable(this.streamPath("title").findFirst().map(
			n -> n.asString()).orElseGet(() -> getJournal().orElse(null)));}
	public String getFirstAuthorName() {
		return this.getFirstAuthor().map(o -> o.getLastName()).orElse("n/a");
	}
	public Optional<String> getJournal() {return this.asString("journal_name");}
	public Optional<Long> getYear() {return getDate().map(d -> (long) d.getYear());}
	
	public Optional<LocalDate> getDate() {
		return this.asString("published_date").map(LocalDate::parse);
	}
	
	public Stream<String> getLicenses() {
		return this.streamPath("oa_locations","URL").map(o -> o.asString());}
	
	//public Optional<Double> getScore() {return this.asDouble("score");}
	
	public List<UnpaywallAuthor> getAuthors() {return this.streamPath(UnpaywallAuthor.class, "z_authors").collect(Collectors.toList());}
	public Optional<String> getAbstract() {return this.asString("abstract");}
	
	public Optional<URI> getTextMiningUri() {
		return Optional.empty();
	}
	
	public Optional<URI> getPdfUri() {
		return Optional.ofNullable(this.streamPath("best_oa_location","url_for_pdf").findFirst()
				.orElseGet(() -> this.streamPath("oa_locations","url_for_pdf").findFirst().orElse(null)))
				.flatMap(n -> {
					try {
						return Optional.of(new URI(n.asString()));
					} catch (URISyntaxException e) {
						return Optional.empty();
					}
				});
	}

	@Override
	public IdType getIdentifierType() {
		return IdType.DOI;
	}

	@Override
	public List<RecordReference> getOtherIdentifiers() {
		return Collections.emptyList();
	}
	
}