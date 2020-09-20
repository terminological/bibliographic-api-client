package uk.co.terminological.bibliography.record;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.co.terminological.bibliography.CiteProcProvider;
import uk.co.terminological.bibliography.CiteProcProvider.Format;

public interface Record extends RecordReference {

	public List<? extends RecordReference> getOtherIdentifiers();
	
	public default Optional<? extends Author> getFirstAuthor() {
		if (getAuthors().isEmpty()) return Optional.empty();
		return Optional.ofNullable(getAuthors().get(0));
	};
	
	public List<? extends Author> getAuthors();
	public Stream<String> getLicenses();
	
	public Optional<String> getAbstract();
	public Optional<String> getTitle();
	public Optional<String> getJournal();
	public Optional<LocalDate> getDate();
		
	public Optional<URI> getPdfUri();
	
	public default Optional<String> getFirstAuthorLastName() {
		return getFirstAuthor().map(a -> a.getLastName());
	}
	
	public default Optional<String> getFirstAuthorFirstName() {
		return getFirstAuthor().flatMap(a -> a.getFirstName());
	}
	
	public default Optional<String> render(String style) {
		if (style == null) return Optional.of(Record.print(this)); 
		try {
			return Optional.of(CiteProcProvider.convert(style, Format.text, this).getEntries()[0]); //.replaceFirst("^\\[[0-9]+\\]", ""));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
	
	public default Set<ImmutableRecordReference> getIdentifiers() {
		Set<ImmutableRecordReference> tmp = new LinkedHashSet<>();
		if(this.getIdentifier().isPresent())
			tmp.add(Builder.recordReference(getIdentifierType(), this.getIdentifier().get()));
		this.getOtherIdentifiers().forEach(i -> {
			if (i.getIdentifier().isPresent()) 
				i.asId().ifComplete(tmp::add);
		});
		return(tmp);
	}
	
	public default Optional<String> getIdentifier(IdType type) {
		if (this.getIdentifierType().equals(type)) return this.getIdentifier();
		return this.getOtherIdentifiers().stream().filter(s -> s.getIdentifierType().equals(type)).flatMap(s -> s.getIdentifier().stream()).findFirst();
	}

	public default Stream<ImmutableRecordReferenceMapping> getMappings() {
		List<ImmutableRecordReference> tmp2 = new ArrayList<>(getIdentifiers());
		List<ImmutableRecordReferenceMapping> tmp3 = new ArrayList<>(); 
		for (int i=0;i<tmp2.size();i++) {
			for (int j=i+1;j<tmp2.size(); j++) {
				tmp3.add(new ImmutableRecordReferenceMapping(tmp2.get(i), tmp2.get(j)));
			}
		}
		return tmp3.stream();
	}
	
	public static String print(Record r) {
		return
				r.getFirstAuthorLastName().orElse("Unknown")+", "+
				r.getFirstAuthorFirstName().orElse("Author")+" - "+
				r.getTitle().orElse("Unknown title")+"; ("+
				r.getDate().map(d -> Integer.toString(d.getYear())).orElse("Unknown year")+") "+
				r.getJournal().orElse("Unknown journal")+". "+
				r.getIdentifiers().stream().map(RecordReference::print).collect(Collectors.joining(" "));
	}
	
}
