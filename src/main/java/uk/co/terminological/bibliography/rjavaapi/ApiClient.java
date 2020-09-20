package uk.co.terminological.bibliography.rjavaapi;

import static uk.co.terminological.jsr223.ROutput.mapping;
import static uk.co.terminological.jsr223.ROutput.toDataframe;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.undercouch.citeproc.CSL;
import uk.co.terminological.bibliography.BibliographicApis;
import uk.co.terminological.bibliography.client.CitedByMapper;
import uk.co.terminological.bibliography.client.CitesMapper;
import uk.co.terminological.bibliography.client.IdMapper;
import uk.co.terminological.bibliography.client.RecordFetcher;
import uk.co.terminological.bibliography.client.Searcher;
import uk.co.terminological.bibliography.crossref.CrossRefWork;
import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.ImmutableRecordReference;
import uk.co.terminological.bibliography.record.MergedRecord;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordReference;
import uk.co.terminological.bibliography.record.RecordReferenceMapping;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.jsr223.RClass;
import uk.co.terminological.jsr223.RInput;
import uk.co.terminological.jsr223.RMethod;
import uk.co.terminological.jsr223.ROutput.Dataframe;



@RClass
public class ApiClient implements Searcher,CitedByMapper,CitesMapper,RecordFetcher,IdMapper {
	
	static Logger log = LoggerFactory.getLogger(ApiClient.class);
	
	public void disableCache() {
		this.api.disableCache();
	}
	
	public void enableCache() {
		this.api.enableCache();
	}
	
	private ApiClient(String configFile, String cacheDir) throws IOException {
		Path filePath = Paths.get(configFile.replaceFirst("^~", System.getProperty("user.home")));
		Path cacheDir2 = Paths.get(cacheDir.replace("^~", System.getProperty("user.home")));
		this.api = BibliographicApis.create(filePath, cacheDir2);
	}
	
	@RMethod
	public ApiClient(String configFile) throws IOException {
		Path filePath = Paths.get(configFile);
		this.api = BibliographicApis.create(filePath);
	}
	
	@RMethod
	public static ApiClient create(String configFile) {
		try {
			return new ApiClient(configFile);
		} catch (IOException e) {
			log.error("could not create api client: {}",e.getMessage());
			return null;
		}
	}
	
	@RMethod
	public static ApiClient createCached(String configFile, String cacheDir) {
		try {
			return new ApiClient(configFile,cacheDir);
		} catch (IOException e) {
			log.error("could not create api client: {}",e.getMessage());
			return null;
		}
		
	}
	
	BibliographicApis api;
	String citationStyle = null;
	List<String> searchApis = getSupportedApis().get("search");
	List<String> citesApis = getSupportedApis().get("cites");
	List<String> citedByApis = getSupportedApis().get("citedBy");
	List<String> fetchApis = getSupportedApis().get("fetch");
	List<String> idMapApis = getSupportedApis().get("mapIds");
	
	@RMethod
	public static Map<String,List<String>> getSupportedApis() {
		return FluentMap
			.with("search", List.of("crossref","entrez","europepmc"))
			.and("cites", List.of("crossref","entrez","europepmc","opencitations"))
			.and("citedBy", List.of("opencitations","entrez","europepmc"))
			.and("fetch", List.of("crossref","entrez","europepmc","unpaywall"))
			.and("idMap", List.of("pmcid"));
	}
	
	Optional<LocalDate> from = Optional.empty();  
	Optional<LocalDate> to = Optional.empty();
	Optional<Integer> limit = Optional.of(50);
	boolean analysePdfs = false;
	
	// Java methods
	
	public Collection<Record> search(String search, Optional<LocalDate> from, Optional<LocalDate> to,
			Optional<Integer> limit) {
		Map<String,String> multiSearch = FluentMap.create();
		for (String api: searchApis) multiSearch.put(api, search);
		return multiSearch(multiSearch, from, to, limit);
	}
	
	public Collection<? extends Record> search(String search) {
		return search(search, from, to, limit);
	};
	
	public Collection<? extends Record> search(String search, Integer limit) {
		return search(search, from, to, Optional.of(limit));
	};
	
	public Collection<? extends Record> search(String search, LocalDate from, LocalDate to) {
		return search(search, Optional.of(from), Optional.of(to), limit);
	};
	
	public Collection<Record> multiSearch(Map<String,String> searchByApi) {
		return multiSearch(searchByApi, from, to, limit);
	}
		
	public Collection<Record> multiSearch(Map<String,String> searchByApi, Optional<LocalDate> from1, Optional<LocalDate> to1,
			Optional<Integer> limit1) {
		Collection<Record> out = new ArrayList<>();
		searchByApi.forEach((searchApi,search) -> {
			Collection<? extends Record> tmp = Collections.emptyList(); 
			if (searchApi.equals("crossref")) {
				tmp = api.getCrossref().search(search, from1, to1, limit1);
			} else if (searchApi.equals("entrez")) {
				tmp = api.getEntrez().search(search, from1, to1, limit1);
			} else if (searchApi.equals("europepmc")) {
				tmp = api.getEuropePMC().search(search, from1, to1, limit1);
			}
			log.info("Search found {} hits in {} for '{}'",tmp.size(),searchApi,search);
			out.addAll(tmp);
		});
		
		return out;
	}
	
	@Override
	public Collection<? extends CitationLink> citesReferences(Collection<? extends RecordReference> refs) {
		Collection<CitationLink> out = new ArrayList<>();
		citesApis.forEach(citesApi -> {
			Collection<? extends CitationLink> tmp = Collections.emptyList(); 
			if (citesApi.equals("crossref")) {
				tmp = api.getCrossref().citesReferences(refs);
			} else if (citesApi.equals("entrez")) {
				tmp = api.getEntrez().citesReferences(refs);
			} else if (citesApi.equals("europepmc")) {
				tmp = api.getEuropePMC().citesReferences(refs);
			} else if (citesApi.equals("opencitations")) {
				tmp = api.getOpenCitationsClient().citesReferences(refs);
			}
			log.info("Cites search found {} references in {} for {} articles",tmp.size(),citesApi,refs.size());
			out.addAll(tmp);
		});
		return out;
	}

	@Override
	public Collection<? extends CitationLink> referencesCiting(Collection<? extends RecordReference> refs) {
		log.info("Searching for articles citing these {} references",refs.size());
		Collection<CitationLink> out = new ArrayList<>();
		citedByApis.forEach(citesApi -> {
			Collection<? extends CitationLink> tmp = Collections.emptyList(); 
			if (citesApi.equals("entrez")) {
				tmp = api.getEntrez().referencesCiting(refs);
			} else if (citesApi.equals("europepmc")) {
				tmp = api.getEuropePMC().referencesCiting(refs);
			} else if (citesApi.equals("opencitations")) {
				tmp = api.getOpenCitationsClient().referencesCiting(refs);
			}
			log.info("Cited by search found {} references in {} for {} articles",tmp.size(),citesApi,refs.size());
			out.addAll(tmp);
		});
		return out;
	}
	
	@Override
	public Map<ImmutableRecordReference, ? extends Record> fetch(Collection<? extends RecordReference> ids) {
		Map<ImmutableRecordReference, MergedRecord> out = new HashMap<>();
		fetchApis.forEach(fetchApi -> {
			Map<ImmutableRecordReference, ? extends Record> tmp = new HashMap<>();
			if (fetchApi.equals("crossref")) {
				tmp = api.getCrossref().fetch(ids);
			} else if (fetchApi.equals("entrez")) {
				tmp = api.getEntrez().fetch(ids);
			} else if (fetchApi.equals("europepmc")) {
				tmp = api.getEuropePMC().fetch(ids);
			} else if (fetchApi.equals("unpaywall")) {
				tmp = api.getUnpaywall().fetch(ids);
			}
			log.info("Fetch found {} records in {} for {} ids",tmp.size(),fetchApi, ids.size());
			tmp.forEach((k,v) -> {
				out.merge(k, MergedRecord.from(v), (v1,v2) -> v1.merge(v2));
			});
		});
		return out;
	}
	
	@Override
	public Set<RecordReferenceMapping> mappings(Collection<? extends RecordReference> ids) {
		Set<RecordReferenceMapping> out = new HashSet<>();
		idMapApis.forEach(citesApi -> {
			Collection<? extends RecordReferenceMapping> tmp = Collections.emptyList(); 
			if (citesApi.equals("entrez")) {
				tmp = api.getEntrez().mappings(ids);
			} else if (citesApi.equals("pmcid")) {
				tmp = api.getPmcIdConv().mappings(ids);
			}
			log.info("Mapping id search found {} mappings in {} for {} ids",tmp.size(),citesApi,ids.size());
			out.addAll(tmp);
		});
		return out;
	}
	
	@RMethod void downloadPdfs(String baseDirectory, List<String> pdfUrls, List<String> names) {
		Path out = Paths.get(baseDirectory);
		for (int i=0; i<pdfUrls.size(); i++) {
			String url = pdfUrls.get(i);
			String name = names.get(i); 
			
			Optional<InputStream> ois = api.getPdfFetcher().getPdfFromUrl(url);
			if (ois.isPresent()) {
				try {
					Files.copy(ois.get(), out.resolve(name), StandardCopyOption.REPLACE_EXISTING);
					log.debug("Downloaded pdf from {}",url);
				} catch (IOException e) {
					log.warn("Could not download pdf from {}",url);
				}
			}
		}
	}
	
	@RMethod
	public Set<CrossRefWork> getReferencesFromPdf(String pdfUrl) throws IOException {
		
		Optional<InputStream> ois = api.getPdfFetcher().getPdfFromUrl(pdfUrl);
		if (ois.isPresent()) {
			
			List<String> refs = api.getPdfFetcher().extractArticleRefs(pdfUrl, ois.get());
			log.debug("Found {} references for {}", refs.size(), pdfUrl);
			
			Set<CrossRefWork> works = refs.stream().flatMap(ref -> {
				return api.getCrossref().findWorkByCitationString(ref).stream();
			}).collect(Collectors.toSet());
			
			ois.get().close();
			return works;
			
		} else {
			log.warn("Could not get pdf from {}",pdfUrl);
			return Collections.emptySet();
		}
	}
	
	// R methods
	
	// Configure apis
	
	@RMethod 
	public ApiClient withCitationStyle(String styleName) {
		this.citationStyle = styleName;
		return this;
	}
	
	@RMethod 
	public ApiClient withSearchDates(String from, String to) {
		try {
			this.from = Optional.ofNullable(from).map(LocalDate::parse);
			this.to = Optional.ofNullable(to).map(LocalDate::parse);
		} catch (DateTimeParseException e) {
			log.error("Unparseable date string in one of: {} or {}",from,to);
		}
		return this;
	}
	
	@RMethod
	public ApiClient withSearchLimit(int limit) {
		this.limit = Optional.of(limit);
		return this;
	}
	
	@RMethod 
	public ApiClient withSearchApis(List<String> apis) {
		this.searchApis = apis;
		return this;
	}
	
	@RMethod 
	public ApiClient withFetchApis(List<String> apis) {
		this.fetchApis = apis;
		return this;
	}
	
	@RMethod 
	public ApiClient withCitesApis(List<String> apis) {
		this.citesApis = apis;
		return this;
	}
	
	@RMethod 
	public ApiClient withCitedByApis(List<String> apis) {
		this.citedByApis = apis;
		return this;
	}
	
	@RMethod 
	public ApiClient withIdMappingApis(List<String> apis) {
		this.idMapApis = apis;
		return this;
	}
	
	@RMethod 
	public Set<String> getSupportedCitationStyles() {
		try {
			return CSL.getSupportedStyles();
		} catch (IOException e) {
			log.warn("couldn't retrieve styles");
			return Collections.emptySet();
		}
	}
	
	// Execute api functions
	
	@RMethod
	public Dataframe searchApis(String searchTerm) {
		return search(searchTerm).stream()
					.collect(recordCollector());
	}

	@RMethod
	public Dataframe multiSearchApis(Map<String,String> searchTermByApi) {
		return multiSearch(searchTermByApi).stream()
					.collect(recordCollector());
	}

	@RMethod
	public Dataframe fetchDetails(List<Map<String,Object>> idDf) {
		Set<ImmutableRecordReference> ids = idsFromDataframe(idDf);
		return fetch(ids).entrySet().stream()
			.collect(
					toDataframe(
							mapping(Entry.class,Cols.ID, s-> ((ImmutableRecordReference) s.getKey()).getIdentifier().orElse(null)),
							mapping(Entry.class,Cols.ID_TYPE, s-> ((ImmutableRecordReference) s.getKey()).getIdentifierType().toString()),
							mapping(Entry.class,Cols.TITLE, s-> ((Record) s.getValue()).getTitle().orElse(null)),
							mapping(Entry.class,Cols.ABSTRACT, s-> ((Record) s.getValue()).getAbstract().orElse(null)),
							mapping(Entry.class,Cols.CITATION, s-> ((Record) s.getValue()).render(citationStyle).orElse(null)),
							mapping(Entry.class,Cols.PMID, s-> ((Record) s.getValue()).getIdentifier(IdType.PMID).orElse(null)),
							mapping(Entry.class,Cols.DOI, s-> ((Record) s.getValue()).getIdentifier(IdType.DOI).orElse(null)),
							mapping(Entry.class,Cols.PMC, s-> ((Record) s.getValue()).getIdentifier(IdType.PMCID).orElse(null)),
							mapping(Entry.class,Cols.URL, s -> ((Record) s.getValue()).getPdfUri().orElse(null)),
							mapping(Entry.class,Cols.DATE, s -> ((Record) s.getValue()).getDate().orElse(null))
							));
	}
	
	private static String nullToStr(Object o) {return o != null? o.toString(): null;}
	
	protected static Set<ImmutableRecordReference> idsFromDataframe(List<Map<String,Object>> idDf) {
		if (idDf.size()==0) return Collections.emptySet();
		if (
				!(idDf.get(0).containsKey(Cols.ID) && idDf.get(0).containsKey(Cols.ID_TYPE))
				&& !idDf.get(0).containsKey(Cols.PMC)
				&& !idDf.get(0).containsKey(Cols.PMID)
				&& !idDf.get(0).containsKey(Cols.DOI)
				
			) {
			log.error("Dataframe must contain an {} and {} column, or any of {}, {}, {}",Cols.ID, Cols.ID_TYPE, Cols.DOI, Cols.PMC, Cols.PMID);
			return Collections.emptySet();
		}
		if(idDf.get(0).containsKey(Cols.ID_TYPE)) {
			return idDf.stream()
				.flatMap(row -> Stream.of( 
						Builder.recordReference(
								IdType.valueOf(row.get(Cols.ID_TYPE).toString().toUpperCase()), 
								nullToStr(row.get(Cols.ID))),
						Builder.recordReference(IdType.DOI, nullToStr(row.get(Cols.DOI))),
						Builder.recordReference(IdType.PMID, nullToStr(row.get(Cols.PMID))),
						Builder.recordReference(IdType.PMCID, nullToStr(row.get(Cols.PMC)))
					)).filter(rr -> rr.isComplete())
				.collect(Collectors.toSet());
		} else {
			return idDf.stream()
					.flatMap(row -> Stream.of( 
							Builder.recordReference(IdType.DOI, nullToStr(row.get(Cols.DOI))),
							Builder.recordReference(IdType.PMID, nullToStr(row.get(Cols.PMID))),
							Builder.recordReference(IdType.PMCID, nullToStr(row.get(Cols.PMC)))
						)).filter(rr -> rr.isComplete())
					.collect(Collectors.toSet());
		}
	}
	
	@RMethod
	public Dataframe citesReferences(List<Map<String,Object>> idDf) {
		Set<ImmutableRecordReference> ids = idsFromDataframe(idDf);
		return citesReferences(ids).stream()
			.collect(citationLinkCollector());
	}
	
	@RMethod
	public Dataframe referencesCiting(List<Map<String,Object>> idDf) {
		Set<ImmutableRecordReference> ids = idsFromDataframe(idDf);
		return referencesCiting(ids).stream()
			.collect(citationLinkCollector());
	}

	@RMethod Dataframe lookupIds(List<Map<String,Object>> idDf) {
		Set<ImmutableRecordReference> ids = idsFromDataframe(idDf);
		return mappings(ids).stream()
			.collect(idMappingCollector());
	}
	
	protected Collector<RecordReferenceMapping, ?, Dataframe> idMappingCollector() {
		return toDataframe(
			mapping(RecordReferenceMapping.class, Cols.SOURCE_ID, s-> s.getSource().getIdentifier().orElse(null)),
			mapping(RecordReferenceMapping.class,Cols.SOURCE_ID_TYPE, s-> s.getSource().getIdentifierType().toString()),
			mapping(RecordReferenceMapping.class, Cols.TARGET_ID, s-> s.getTarget().getIdentifier().orElse(null)),
			mapping(RecordReferenceMapping.class,Cols.TARGET_ID_TYPE, s-> s.getTarget().getIdentifierType().toString())
		);
	}

	protected Collector<CitationLink, ?, Dataframe> citationLinkCollector() {
		return toDataframe(
			mapping(CitationLink.class, Cols.SOURCE_ID, s-> s.getSource().getIdentifier().flatMap(rr -> rr.getIdentifier()).orElse(null)),
			mapping(CitationLink.class,Cols.SOURCE_ID_TYPE, s-> s.getSource().getIdentifier().map(rr -> rr.getIdentifierType()).orElse(null)),
			mapping(CitationLink.class,Cols.SOURCE_TITLE, s-> s.getSource().getTitle().orElse(null)),
			mapping(CitationLink.class, Cols.TARGET_ID, s-> s.getTarget().getIdentifier().flatMap(rr -> rr.getIdentifier()).orElse(null)),
			mapping(CitationLink.class,Cols.TARGET_ID_TYPE, s-> s.getTarget().getIdentifier().map(rr -> rr.getIdentifierType()).orElse(null)),
			mapping(CitationLink.class,Cols.TARGET_TITLE, s-> s.getTarget().getTitle().orElse(null)),
			mapping(CitationLink.class,Cols.CITATION_INDEX, s-> s.getIndex().orElse(null))
		);
	}

	protected Collector<Record, ?, Dataframe> recordCollector() {
		return toDataframe(
			mapping(Record.class,Cols.TITLE, s-> s.getTitle().orElse(null)),
			mapping(Record.class,Cols.ABSTRACT, s-> s.getAbstract().orElse(null)),
			mapping(Record.class,Cols.CITATION, s-> s.render(citationStyle).orElse(null)),
			mapping(Record.class,Cols.PMID, s-> s.getIdentifier(IdType.PMID).orElse(null)),
			mapping(Record.class,Cols.DOI, s-> s.getIdentifier(IdType.DOI).orElse(null)),
			mapping(Record.class,Cols.PMC, s-> s.getIdentifier(IdType.PMCID).orElse(null)),
			mapping(Record.class,Cols.ID, s -> s.getIdentifier().orElse(null)),
			mapping(Record.class,Cols.ID_TYPE, s -> s.getIdentifierType().toString()),
			mapping(Record.class,Cols.DATE, s -> s.getDate().orElse(null)),
			mapping(Record.class,Cols.URL, s -> s.getPdfUri().orElse(null)));
	}

	@RMethod
	public Repository getRepository() {
		return new Repository(this);
	}
	
	@RMethod
	public Repository loadRepository(String filename) throws IOException, ClassNotFoundException {
		filename = filename.replaceFirst("^~", System.getProperty("user.home"));
	    FileInputStream in = new FileInputStream(filename);
	    ObjectInputStream s = new ObjectInputStream(in);
	    Repository out = (Repository) s.readObject();
	    in.close();
	    out.setApi(this);
	    return out;
	}
	
	
	
	
	
}
