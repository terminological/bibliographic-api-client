package uk.co.terminological.bibliography.europepmc;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;

import org.isomorphism.util.TokenBuckets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import uk.co.terminological.bibliography.CachingApiClient;
import uk.co.terminological.bibliography.client.CitedByMapper;
import uk.co.terminological.bibliography.client.CitesMapper;
import uk.co.terminological.bibliography.client.IdLocator;
import uk.co.terminological.bibliography.client.Searcher;
import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordIdentifier;
import uk.co.terminological.bibliography.record.RecordReference;

public class EuropePMCClient extends CachingApiClient implements Searcher,IdLocator,CitesMapper,CitedByMapper {

	// https://europepmc.org/RestfulWebService
	
	// ####### Constructors / factories etc ####### //
	
	// private static Logger log = LoggerFactory.getLogger(EuropePmcClient.class);
	
	private EuropePMCClient(Optional<Path> optional, String developerEmail) {
		super(optional,
				TokenBuckets.builder()
				.withCapacity(50)
				.withInitialTokens(50)
				.withFixedIntervalRefillStrategy(50, 1, TimeUnit.SECONDS).build());
		this.developerEmail = developerEmail;	
	}
	
	private static String baseUrl = "https://www.ebi.ac.uk/europepmc/webservices/rest/"; //search?query=malaria&format=json

	private String developerEmail;
	private ObjectMapper objectMapper = new ObjectMapper();
	
	@Override
	protected MultivaluedMap<String, String> defaultApiParams() {
		MultivaluedMap<String, String> out = new MultivaluedMapImpl();
		out.add("format", "json");
		out.add("pageSize", "1000");
		if (developerEmail != null) out.add("email", developerEmail);
		return out;
	}
	
	private static EuropePMCClient singleton;
	
	public static  EuropePMCClient create(String developerEmail) {
		return create(developerEmail, null);
	}
	
	public static  EuropePMCClient create(String developerEmail, Path cacheDir) {
		if (singleton == null) singleton = new EuropePMCClient(Optional.ofNullable(cacheDir), developerEmail);
		return singleton;
	}
	
	
	
	// ####### API methods ####### //
	
	public Optional<EuropePMCCoreResult> getById(String id, IdType type) {
		EuropePMCListResult<EuropePMCCoreResult> tmp;
		if( type.equals(IdType.DOI)) {
			tmp = fullSearch("DOI:"+id);
		} else if( type.equals(IdType.PMCID)) {
			tmp = fullSearch("PMCID:"+id);
		} else if( type.equals(IdType.PMID)) {
			//EXT_ID:16199517
			tmp = fullSearch("EXT_ID:"+id);
		} else {
			tmp = fullSearch(id);
		}
		return tmp.getItems()
				
				.findFirst();
	}
	
	public QueryBuilder buildQuery(String searchTerm) {
		return new QueryBuilder(defaultApiParams(),searchTerm,this);
	}
	
	public static class QueryBuilder {
		MultivaluedMap<String, String> searchParams;
		EuropePMCClient client;
		
		protected QueryBuilder(MultivaluedMap<String, String> searchParams,String searchTerm, EuropePMCClient client) {
			this.searchParams = searchParams;
			this.searchParams.add("query", searchTerm);
			this.client = client;
		}
		
		public QueryBuilder useSynonyms() {
			this.searchParams.add("synonym", "true");
			return this;
		}
		
		public QueryBuilder withSort(Field field, Direction dir) {
			this.searchParams.add("sort", field.toString()+" "+dir.toString());
			return this;
		}
		
		public Optional<EuropePMCListResult.Lite> executeLite() {
			this.searchParams.add("resultType", "lite");
			return client.buildCall(baseUrl+"searchPost", EuropePMCListResult.Lite.class)
					.withParams(searchParams)
					.withOperation(is -> 
						new EuropePMCListResult.Lite(client.objectMapper.readTree(is))
					).post();
					
		}
		
		public Optional<EuropePMCListResult.Core> executeFull() {
			this.searchParams.add("resultType", "core");
			return client.buildCall(baseUrl+"searchPost", EuropePMCListResult.Core.class)
					.withParams(searchParams)
					.withOperation(is -> 
						new EuropePMCListResult.Core(client.objectMapper.readTree(is))
					).post();
		}
		
	}
	
	public static enum Field {
		P_PDATE_D, AUTH_FIRST, CITED
	}
	
	public static enum Direction {
		ASC, DESC; public String toString() {return this.name().toLowerCase();}
	}
		
	public EuropePMCListResult<EuropePMCLiteResult> liteSearch(String text) {
		// https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:10.1038/nature09534&sort=CITED%20desc&format=json
		// https://www.ebi.ac.uk/europepmc/webservices/rest/searchPOST
		// query=malaria%20sort_cited:y
		// format=json
		// sort=CITED desc (P_PDATE_D, AUTH_FIRST, CITED (see https://www.ebi.ac.uk/europepmc/webservices/rest/fields))
		// pageSize=1000 (max)
		// cursorMark=* (paging...)
		// synonym=false/true
		// resultType=idlist/lite/core
		// POST
		return buildQuery(text).executeLite().get();
	}
	
	public EuropePMCListResult<EuropePMCCoreResult> fullSearch(String text) {
		// https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:10.1038/nature09534&sort=CITED%20desc&format=json
		// https://www.ebi.ac.uk/europepmc/webservices/rest/searchPOST
		// query=malaria%20sort_cited:y
		// format=json
		// sort=CITED desc (P_PDATE_D, AUTH_FIRST, CITED (see https://www.ebi.ac.uk/europepmc/webservices/rest/fields))
		// pageSize=1000 (max)
		// cursorMark=* (paging...)
		// synonym=false/true
		// resultType=idlist/lite/core
		// POST
		return buildQuery(text).executeFull().get();
	}
	
	public EuropePMCListResult<EuropePMCCitation> citations(DataSources source, String id) {
		//https://www.ebi.ac.uk/europepmc/webservices/rest/MED/9843981/citations?format=json
		//id=23245604
		//source=MED
		//page=0
		//pageSize=1000 (max)
		//format=json
		return this.buildCall(baseUrl+source+"/"+id+"/citations", EuropePMCListResult.Citation.class)
				.withParams(defaultApiParams())
				.withOperation(is -> 
					new EuropePMCListResult.Citation(objectMapper.readTree(is))
				).get().get();
	}
	
	public EuropePMCListResult<EuropePMCReference> references(DataSources source, String id) {
		//https://www.ebi.ac.uk/europepmc/webservices/rest/MED/9843981/references?format=json
		//id=23245604
		//source=MED
		//page=0
		//pageSize=1000 (max)
		//format=json
		return this.buildCall(baseUrl+source+"/"+id+"/references", EuropePMCListResult.Reference.class)
				.withParams(defaultApiParams())
				.withOperation(is -> 
					new EuropePMCListResult.Reference(objectMapper.readTree(is))
				).get().get();
	}
	
	
	
	public static enum DataSources {
		AGR, CBA, CTX, ETH, HIR, MED, NBK, PAT, PMC
	}

	@Override
	public Collection<? extends CitationLink> referencesCiting(Collection<RecordReference> ref) {
		
		List<CitationLink> out = new ArrayList<>();
		
		for (RecordReference r: ref) {
			
			if (r.getIdentifierType().equals(IdType.PMID)) {
				int i = 0;
				for (EuropePMCReference r2: references(DataSources.MED,r.getIdentifier().get()).getItems().collect(Collectors.toList())) {
					out.add(Builder.citationLink(
							Builder.citationReference(r, null, null), 
							Builder.citationReference(Optional.of(r2), r2.getTitle(), Optional.empty()), 
							Optional.of(i)));
				}
			}
			
			if (r.getIdentifierType().equals(IdType.PMCID)) {
				int i = 0;
				for (EuropePMCReference r2: references(DataSources.PMC,r.getIdentifier().get()).getItems().collect(Collectors.toList())) {
					out.add(Builder.citationLink(
							Builder.citationReference(r, null, null), 
							Builder.citationReference(Optional.of(r2), r2.getTitle(), Optional.empty()), 
							Optional.of(i)));
				}
			}
		}
		
		return out;
		
	}

	@Override
	public Set<? extends CitationLink> citesReferences(Collection<RecordReference> ref) {
		
		Set<CitationLink> out = new HashSet<>();
		
		for (RecordReference r: ref) {
			
			if (r.getIdentifierType().equals(IdType.PMID)) {
				int i = 0;
				for (EuropePMCCitation r2: citations(DataSources.MED,r.getIdentifier().get()).getItems().collect(Collectors.toList())) {
					out.add(Builder.citationLink(
							Builder.citationReference(r, null, null), 
							Builder.citationReference(Optional.of(r2), r2.getTitle(), Optional.empty()), 
							Optional.of(i)));
				}
			}
			
			if (r.getIdentifierType().equals(IdType.PMCID)) {
				int i = 0;
				for (EuropePMCCitation r2: citations(DataSources.PMC,r.getIdentifier().get()).getItems().collect(Collectors.toList())) {
					out.add(Builder.citationLink(
							Builder.citationReference(r, null, null), 
							Builder.citationReference(Optional.of(r2), r2.getTitle(), Optional.empty()), 
							Optional.of(i)));
				}
			}
		}
		
		return out;
	}

	@Override
	public Map<RecordIdentifier, ? extends Record> getById(Collection<RecordReference> equivalentIds) {
		Map<RecordIdentifier, Record> out = new HashMap<>();
		
		for (RecordReference rr : equivalentIds) {
			if (rr.getIdentifier().isPresent()) {
				this.getById(rr.getIdentifier().get(), rr.getIdentifierType()).ifPresent(r -> out.put(Builder.recordReference(rr), r));
			}
		}
		
		return out;
	}

	@Override
	public Collection<? extends Record> search(String search, Optional<LocalDate> from, Optional<LocalDate> to,
			Optional<Integer> limit) {
		return this.buildQuery(search).executeFull().stream().flatMap(c -> c.getItems()).collect(Collectors.toList());
		
	}
	
}
