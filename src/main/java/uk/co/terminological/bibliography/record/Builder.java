package uk.co.terminological.bibliography.record;

public class Builder {

	public static ImmutableCitationReference citationReference(CitationReference link) {
		if (link == null) return null;
		if (link instanceof ImmutableCitationReference) return (ImmutableCitationReference) link;
		return new ImmutableCitationReference(link);
	}
	
	public static ImmutableCitationLink citationLink(CitationLink link) {
		if (link == null) return null;
		if (link instanceof ImmutableCitationLink) return (ImmutableCitationLink) link;
		return new ImmutableCitationLink(link);
	}
	
	public static ImmutablePrintReference printReference(PrintReference link) {
		if (link == null) return null;
		if (link instanceof ImmutablePrintReference) return (ImmutablePrintReference) link;
		return new ImmutablePrintReference(link);
	}
	
	public static ImmutableAuthor author(Author link) {
		if (link == null) return null;
		if (link instanceof ImmutableAuthor) return (ImmutableAuthor) link;
		return new ImmutableAuthor(link);
	}
	
	public static ImmutableRecord record(Record link) {
		if (link == null) return null;
		if (link instanceof ImmutableRecord) return (ImmutableRecord) link;
		return new ImmutableRecord(link);
	}
	
	public static ImmutableRecordReference recordReference(RecordReference link) {
		if (link == null) return null;
		if (link instanceof ImmutableRecordReference) return (ImmutableRecordReference) link;
		return new ImmutableRecordReference(link);
	}
	
	
//	public static ImmutableCitationLink citationLink(CitationReference source, CitationReference target, Optional<Integer> indexOf) {
//		
//		return new ImmutableCitationLink() {
//			ImmutableCitationReference source = Builder.citationReference(source);
//			ImmutableCitationReference target = Builder.citationReference(target);
//			Integer index = indexOf.orElse(null); 
//			@Override
//			public ImmutableCitationReference getSource() {
//				return source;
//			}
//			@Override
//			public ImmutableCitationReference getTarget() {
//				return target;
//			}
//			@Override
//			public Optional<Integer> getIndex() {
//				return Optional.of(index);
//			};
//		};
//	}
	
//	public static ImmutableCitationReference citationReference(
//			Optional<RecordReference> identifier,
//			Optional<String> title,
//			Optional<PrintReference> bibliographicId
//			) {
//		return new CitationReference() {
//
//			@Override
//			public Optional<RecordReference> getIdentifier() {
//				return identifier;
//			}
//
//			@Override
//			public Optional<String> getTitle() {
//				return title;
//			}
//
//			@Override
//			public Optional<PrintReference> getBibliographicId() {
//				return bibliographicId;
//			}
//			
//		};
//	}
	
//	public static CitationReference citationReference(
//			RecordReference identifier,
//			String title,
//			PrintReference bibliographicId
//			) {
//		return citationReference(
//				Optional.ofNullable(identifier),
//				Optional.ofNullable(title),
//				Optional.ofNullable(bibliographicId)
//		);
//	}
	
//	public static Optional<ImmutableRecordReference> recordReference(RecordReference ref) {
//		if (ref instanceof ImmutableRecordReference) return Optional.of((ImmutableRecordReference) ref);
//		if (!ref.getIdentifier().isPresent()) return Optional.empty();			
//		ImmutableRecordReference out = new ImmutableRecordReference();
//		out.id = ref.getIdentifier().get();
//		out.idType = ref.getIdentifierType();
//		return Optional.of(out);
//	}
	
	public static ImmutableRecordReference recordReference(IdType idType, String id) {
		ImmutableRecordReference out = new ImmutableRecordReference(idType, id);
		return out;
	}
	
//	public static Optional<ImmutableRecordReferenceMapping> recordIdMapping(RecordReference source, RecordReference target) {
//		if (!Builder.recordReference(source).isPresent() || !Builder.recordReference(source).isPresent()) return Optional.empty();
//		return Optional.of(new ImmutableRecordReferenceMapping(
//				Builder.recordReference(source).get(), 
//				Builder.recordReference(target).get()));
//	}

	
}
