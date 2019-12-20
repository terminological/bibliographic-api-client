package uk.co.terminological.bibliography;

import static uk.co.terminological.jsr223.ROutput.mapping;
import static uk.co.terminological.jsr223.ROutput.toDataframe;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordIdentifierMapping;
import uk.co.terminological.jsr223.RClass;
import uk.co.terminological.jsr223.RMethod;

@RClass
public class RJavaApi {
	
	BibliographicApis api;
	
	@RMethod
	public RJavaApi(String configFilename) throws IOException {
		this.api = BibliographicApis.create(Paths.get(configFilename));
	}
	
	@RMethod
	public List<String> getSupportedApis() {
		return Arrays.asList("crossref","entrez","europepmc","opencitations","pmcid","unpaywall");
	}
	
	@RMethod
	public Map<String,Object[]> searchAllApis(String searchTerm, String citationStyle) {
		return searchSelectedApis(searchTerm, citationStyle, getSupportedApis());
	}
	
	@RMethod
	public Map<String,Object[]> searchSelectedApis(String searchTerm, String citationStyle, List<String> apis) {
		Collection<Record> out = new ArrayList<>();
		if (apis.contains("crossref")) {
			out.addAll(api.getCrossref().search(searchTerm));
			//TODO: store search result and update ID mapping
		}
		if (apis.contains("entrez")) {
			out.addAll(api.getEntrez().search(searchTerm));
			//TODO: store search result and update ID mapping
		}
		if (apis.contains("europepmc")) {
			out.addAll(api.getEuropePMC().search(searchTerm));
			//TODO: store search result and update ID mapping
		}
		return recordsToDataFrame(out,citationStyle); 
	}
	
	
	
	public Map<String,Object[]> recordsToDataFrame(Collection<? extends Record> records,String style) {
		return 
			records
			.stream()
			.collect(toDataframe(
					mapping(Record.class,"abstract", s-> s.getAbstract().orElse(null)),
					mapping(Record.class,"citation", s-> s.render(style).orElse(null)),
					mapping(Record.class,"idType", s-> s.getIdentifierType().toString()),
					mapping(Record.class,"id", s-> s.getIdentifier().orElse(null))
					));
		
	}
	
	public Map<String,Object[]> citationsToDataFrame(Collection<? extends CitationLink> citations) {
		return 
			citations
			.stream()
			.filter(s -> s.getSource().getIdentifier().isPresent())
			.filter(s -> s.getSource().getIdentifier().get().getIdentifier().isPresent())
			.filter(s -> s.getTarget().getIdentifier().isPresent())
			.filter(s -> s.getTarget().getIdentifier().get().getIdentifier().isPresent())
			.collect(toDataframe(
					mapping(CitationLink.class,"sourceId", s-> s.getSource().getIdentifier().get().getIdentifier().get()),
					mapping(CitationLink.class,"sourceIdType", s-> s.getSource().getIdentifier().get().getIdentifierType()),
					mapping(CitationLink.class,"targetId", s-> s.getTarget().getIdentifier().get().getIdentifier().get()),
					mapping(CitationLink.class,"targetIdType", s-> s.getTarget().getIdentifier().get().getIdentifierType()),
					mapping(CitationLink.class,"order", s-> s.getIndex().orElse(null))
					));
	}
	
	public Map<String,Object[]> idMappingToDataFrame(Collection<? extends RecordIdentifierMapping> idMappings) {
		return 
				idMappings
				.stream()
				.filter(s -> s.getSource().getIdentifier().isPresent())
				.filter(s -> s.getTarget().getIdentifier().isPresent())
				.collect(toDataframe(
						mapping(RecordIdentifierMapping.class,"sourceId", s-> s.getSource().getIdentifier().get()),
						mapping(RecordIdentifierMapping.class,"sourceIdType", s-> s.getSource().getIdentifierType()),
						mapping(RecordIdentifierMapping.class,"targetId", s-> s.getTarget().getIdentifier().get()),
						mapping(RecordIdentifierMapping.class,"targetIdType", s-> s.getTarget().getIdentifierType())
						));
	}
	
	
}
