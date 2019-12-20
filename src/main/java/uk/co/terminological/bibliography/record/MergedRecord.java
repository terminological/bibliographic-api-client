package uk.co.terminological.bibliography.record;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergedRecord implements Record {

	List<Record> records = new ArrayList<>();
	
	public static MergedRecord from(Record r) {
		MergedRecord out = new MergedRecord();
		return out.merge(r);
	}
	
	public MergedRecord merge(Record r) {
		//TODO: Check for duplicates before merge
		records.add(r);
		return this;
	}
	
	@Override
	public Optional<String> getIdentifier() {
		return records.stream().flatMap(r -> r.getIdentifier().stream()).findAny();
	}

	@Override
	public IdType getIdentifierType() {
		return records.stream().map(r -> r.getIdentifierType()).findAny().get();
	}

	@Override
	public List<RecordReference> getOtherIdentifiers() {
		//TODO: resolve duplicates
		return records.stream().flatMap(r -> r.getOtherIdentifiers().stream()).collect(Collectors.toList());
	}

	@Override
	public List<? extends Author> getAuthors() {
		//TODO: resolve duplicates
		return records.stream().flatMap(r -> r.getAuthors().stream()).collect(Collectors.toList());
	}

	@Override
	public Stream<String> getLicenses() {
		return records.stream().flatMap(r -> r.getLicenses());
	}

	@Override
	public Optional<String> getAbstract() {
		return records.stream().flatMap(r -> r.getAbstract().stream()).findAny();
	}

	@Override
	public Optional<String> getTitle() {
		return records.stream().flatMap(r -> r.getTitle().stream()).findAny();
	}

	@Override
	public Optional<String> getJournal() {
		return records.stream().flatMap(r -> r.getJournal().stream()).findAny();
	}

	@Override
	public Optional<LocalDate> getDate() {
		return records.stream().flatMap(r -> r.getDate().stream()).findAny();
	}

	@Override
	public Optional<URI> getPdfUri() {
		return records.stream().flatMap(r -> r.getPdfUri().stream()).findAny();
	}

	
	
}
