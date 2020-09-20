package uk.co.terminological.bibliography.record;

import java.util.Optional;

public interface CitationReference {

	Optional<RecordReference> getIdentifier();
	Optional<String> getTitle();
	Optional<PrintReference> getBibliographicId();
	
	public default boolean isComplete() {
		return getIdentifier().isPresent() && getIdentifier().get().isComplete();
	}
	
	public static CitationReference create(
			final RecordReference identifier,
			final String title,
			final PrintReference bibliographicInfo) {
		return new CitationReference() {

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
				return Optional.ofNullable(bibliographicInfo);
			}
			
		};
	}
	
	public static String print(CitationReference cr) {
		return
			cr.getTitle().orElse("No title")+" "+
			cr.getIdentifier().map(RecordReference::print).orElse("No identifier");
		
	}
	
}
