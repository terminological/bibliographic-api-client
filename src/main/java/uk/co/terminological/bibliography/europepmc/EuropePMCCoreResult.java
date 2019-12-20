package uk.co.terminological.bibliography.europepmc;

import static uk.co.terminological.bibliography.record.Builder.recordReference;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import uk.co.terminological.bibliography.record.Author;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.PrintRecord;
import uk.co.terminological.bibliography.record.RecordReference;

public class EuropePMCCoreResult extends EuropePMCLiteResult implements PrintRecord {

	//https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:10.1073/pnas.0506580102&sort=CITED%20desc&format=json&resultType=core
	/*
	 * authorList
author
fullName
firstName
lastName
initials
authorId
affiliation
affiliationOrgId
	 */
	
	public EuropePMCCoreResult(JsonNode node) { super(node); }

	@Override
	public List<RecordReference> getOtherIdentifiers() {
		List<RecordReference> tmp = new ArrayList<>();
		getDOI().map(d -> recordReference(IdType.DOI,d)).ifPresent(tmp::add);
		getPMCID().map(d -> recordReference(IdType.PMCID,d)).ifPresent(tmp::add);
		getPMID().map(d -> recordReference(IdType.PMID,d)).ifPresent(tmp::add);
		getIdentifier().map(d -> recordReference(getIdentifierType(),d)).ifPresent(tmp::add);
		return tmp;
	}

	@Override
	public List<? extends Author> getAuthors() {
		return this.streamPath(EuropePMCAuthor.class, "authorList","author").collect(Collectors.toList());
	}

	@Override
	public Stream<String> getLicenses() {
		return this.asString("license").stream();
	}

	@Override
	public Optional<String> getAbstract() {
		return this.asString("abstractText");
	}

	@Override
	public Optional<LocalDate> getDate() {
		return this.asString("firstPublicationDate ").map(d -> LocalDate.parse(d));
	}

	@Override
	public Optional<URI> getPdfUri() {
		return this.streamPath("fullTextUrlList","fullTextUrl")
			.filter(n -> n.asString("documentStyle").orElse("XXX").equals("PDF"))
			.flatMap(n -> n.asString("url").stream())
			.map(n -> URI.create(n))
			.findFirst();
	}

	
}
