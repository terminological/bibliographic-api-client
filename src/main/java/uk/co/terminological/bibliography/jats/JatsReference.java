package uk.co.terminological.bibliography.jats;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.co.terminological.bibliography.record.Author;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Raw;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordReference;
import uk.co.terminological.fluentxml.XmlElement;
import uk.co.terminological.fluentxml.XmlException;

public class JatsReference implements RecordReference, Record, Raw<XmlElement> {

	private XmlElement raw;
	public JatsReference(XmlElement raw) {this.raw = raw;}
	@Override
	public XmlElement getRaw() {
		return raw;
	}
	
	@Override
	public Optional<String> getIdentifier() {
		try {
			return raw.doXpath("pub-id[@pub-id-type='doi']")
					.get(XmlElement.class).flatMap(o -> o.getTextContent());
		} catch (XmlException e) {
			return Optional.empty();
		}
	}

	@Override
	public IdType getIdentifierType() {
		return IdType.DOI;
	}

	@Override
	public List<? extends RecordReference> getOtherIdentifiers() {
		return Collections.emptyList();
	}

	@Override
	public List<? extends Author> getAuthors() {
		return raw
				.childElements("string-name")
				.stream()
				.map(o -> new JatsCitedAuthor(o)).collect(Collectors.toList());
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
	public Optional<String> getTitle() {
		if (raw.getAttributeValue("publication-type").map(s -> s == "journal").orElse(Boolean.FALSE)) { 
			return raw
				.childElements("article-title")
				.findFirst().flatMap(el -> el.getTextContent())
				.or(() ->
					raw
					.childElements("source")
					.findFirst().flatMap(el -> el.getTextContent())
				);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Optional<String> getJournal() {
		return raw
		.childElements("source")
		.findFirst().flatMap(el -> el.getTextContent());
	}

	@Override
	public Optional<LocalDate> getDate() {
		Optional<Integer> year = raw.childElements("year").findFirst().flatMap(el2 -> el2.getTextContent()).map(Integer::parseInt);
		Optional<Integer> month = raw.childElements("month").findFirst().flatMap(el2 -> el2.getTextContent()).map(Integer::parseInt);
		Optional<Integer> day = raw.childElements("day").findFirst().flatMap(el2 -> el2.getTextContent()).map(Integer::parseInt);
		return year.map(y -> LocalDate.of(y, month.orElse(1), day.orElse(1)));
	}

	@Override
	public Optional<URI> getPdfUri() {
		return Optional.empty();
	}
	

}
