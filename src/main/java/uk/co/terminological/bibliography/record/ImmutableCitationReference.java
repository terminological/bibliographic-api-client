package uk.co.terminological.bibliography.record;

import java.io.Serializable;
import java.util.Optional;

public class ImmutableCitationReference implements CitationReference, Serializable {

	public ImmutableCitationReference(CitationReference ref) {
		this.identifier = ref.getIdentifier().map(Builder::recordReference).orElse(null);
		this.title = ref.getTitle().orElse(null);
		this.print = ref.getBibliographicId().map(Builder::printReference).orElse(null);
	}

	public ImmutableCitationReference(RecordReference crossRefWork, String title, PrintReference crossRefWork2) {
		this.identifier = Builder.recordReference(crossRefWork);
		this.title = title;
		this.print = Builder.printReference(crossRefWork2);
	}

	ImmutableRecordReference identifier;
	String title;
	ImmutablePrintReference print;
	
	@Override
	public Optional<RecordReference> getIdentifier() {
		return Optional.ofNullable(identifier);
	}

	@Override
	public Optional<String> getTitle() {
		return Optional.ofNullable(title);
	}

	@Override
	public Optional<PrintReference> getBibliographicId() {
		return Optional.ofNullable(print);
	}

}
