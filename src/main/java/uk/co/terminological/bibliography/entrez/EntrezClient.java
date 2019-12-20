package uk.co.terminological.bibliography.entrez;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.JAXBException;

import org.ehcache.Cache;
import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import uk.co.terminological.bibliography.BibliographicApiException;
import uk.co.terminological.bibliography.BinaryData;
import uk.co.terminological.bibliography.CachingApiClient;
import uk.co.terminological.bibliography.PdfFetcher;
import uk.co.terminological.bibliography.client.CitedByMapper;
import uk.co.terminological.bibliography.client.CitesMapper;
import uk.co.terminological.bibliography.client.IdLocator;
import uk.co.terminological.bibliography.client.IdMapper;
import uk.co.terminological.bibliography.client.Searcher;
import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordIdentifier;
import uk.co.terminological.bibliography.record.RecordIdentifierMapping;
import uk.co.terminological.bibliography.record.RecordReference;
import uk.co.terminological.fluentxml.Xml;
import uk.co.terminological.fluentxml.XmlException;

/*
 * http://www.ncbi.nlm.nih.gov/books/NBK25500/
 */
public class EntrezClient extends CachingApiClient implements Searcher, IdLocator, CitesMapper, CitedByMapper, IdMapper {

	// TODO: integrate CSL: https://michel-kraemer.github.io/citeproc-java/api/1.0.1/de/undercouch/citeproc/csl/CSLItemDataBuilder.html 
	// TODO: caching with https://hc.apache.org/httpcomponents-client-ga/tutorial/html/caching.html#storage and EHCache

	private String apiKey;
	private String appId;
	private String developerEmail;
	//private WebResource eSearchResource;
	//private WebResource eFetchResource;
	//private WebResource eLinkResource;
	//private String baseUrl;
	private static final Logger logger = LoggerFactory.getLogger(EntrezClient.class);
	private static final String ESEARCH = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
	private static final String EFETCH = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
	private static final String ELINK = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/elink.fcgi";
	
	private static Map<String, EntrezClient> singleton = new HashMap<>();

	public static EntrezClient create(String apiKey, String appId, String developerEmail) {
		return create(apiKey,appId,developerEmail,null);
	}
	
	public static EntrezClient create(String apiKey, String appId, String developerEmail, Path cache) {

		if (!singleton.containsKey(apiKey)) {
			EntrezClient tmp = new EntrezClient(apiKey, appId, developerEmail, Optional.ofNullable(cache));
			singleton.put(apiKey, tmp);
		}
		return singleton.get(apiKey);

	}
	
	// "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/"
	private EntrezClient(String apiKey, String appId, String developerEmail, Optional<Path> cache) {
		super(cache, 
				 TokenBuckets.builder().withInitialTokens(10).withCapacity(10).withFixedIntervalRefillStrategy(10, 1, TimeUnit.SECONDS).build()
				);
		//this.baseUrl = baseUrl;
		//eSearchResource = client.resource(this.baseUrl + ESEARCH);
		//eFetchResource = client.resource(this.baseUrl + EFETCH);
		//eLinkResource = client.resource(this.baseUrl + ELINK);
		this.apiKey = apiKey;
		this.appId = appId;
		this.developerEmail = developerEmail;
	}

	protected MultivaluedMap<String, String> defaultApiParams() {
		MultivaluedMap<String, String> out = new MultivaluedMapImpl();
		out.add("api_key", apiKey);
		out.add("tool", appId);
		out.add("email", developerEmail);
		return out;
	}

	public ESearchQueryBuilder buildSearchQuery(String searchTerm) {
		return new ESearchQueryBuilder(defaultApiParams(), searchTerm, this);
	}

	public static class ESearchQueryBuilder {
		MultivaluedMap<String, String> searchParams;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		
		boolean empty=false;
		EntrezClient client;

		protected WebResource get(WebResource searchService) {
			WebResource tdmCopy = searchService;
			return tdmCopy.queryParams(searchParams);
		}
		
		protected <X> X post(WebResource searchService, Class<X> output) {
			WebResource tdmCopy = searchService;
			return tdmCopy.post(output,searchParams);
		}

		protected ESearchQueryBuilder(MultivaluedMap<String, String> searchParams, String searchTerm, EntrezClient client) {
			this.searchParams = searchParams;
			this.searchParams.add("term", searchTerm);
			if (searchTerm.isEmpty()) empty=true;
			this.searchParams.add("db", "pubmed");
			this.searchParams.add("retmax", "100000");
			this.searchParams.add("usehistory", "y");
			this.client = client;
		}

		public ESearchQueryBuilder searchDatabase(Database db) {
			searchParams.remove("db");
			this.searchParams.add("db", db.name().toLowerCase());
			return this;
		}

		public ESearchQueryBuilder limit(int from, int count) {
			searchParams.remove("retstart");
			searchParams.remove("retmax");
			this.searchParams.add("retstart", Integer.toString(from));
			this.searchParams.add("retmax", Integer.toString(count));
			return this;
		}

		public ESearchQueryBuilder withinLastDays(int count) {
			searchParams.remove("reldate");
			searchParams.remove("datetype");
			this.searchParams.add("datetype", "edat");
			this.searchParams.add("reldate", Integer.toString(count));
			return this;
		}

		public ESearchQueryBuilder betweenDates(LocalDate start, LocalDate end) {
			searchParams.remove("mindate");
			searchParams.remove("maxdate");
			searchParams.remove("datetype");
			this.searchParams.add("datetype", "edat");
			this.searchParams.add("mindate", start.format(formatter));
			this.searchParams.add("maxdate", end.format(formatter));
			return this;
		}

		public ESearchQueryBuilder restrictSearchToField(String field) {
			searchParams.remove("field");
			this.searchParams.add("field", field);
			return this;
		}
		
		public Optional<EntrezSearch> execute() {
			if (empty) return Optional.empty();
			return client.buildCall(ESEARCH, EntrezSearch.class)
					.withParams(searchParams)
					.withOperation(is -> {
						Xml resp = Xml.fromStream(is);
						return new EntrezSearch(resp.content());
					}).post();
			
		}
		
		public String toString() {return keyFromApiQuery(ESEARCH,searchParams); }
	}

	//public static class ELinkQueryBuilder

	public static enum Database { PUBMED, PMC, MESH, GENE;
		public String toString() {return this.name().toLowerCase();}
	}

	/**
	 * Retrieve PMIDs from PubMed for a search string
	 * 
	 * @param searchTerm
	 *            search string (same as Pubmed web interface)
	 * @return
	 * @throws BibliographicApiException 
	 * @throws JAXBException
	 
	public EntrezResult.Search search(ESearchQueryBuilder builder) throws BibliographicApiException {
		logger.debug("making esearch query with params {}", builder.toString());
		rateLimiter.consume();
		InputStream is = builder.get(eSearchResource).post(InputStream.class);
		
		try {
			Xml resp = Xml.fromStream(is);
			return new EntrezResult.Search(resp.content());
		} catch (XmlException e1) {
			throw new BibliographicApiException("could not parse result",e1);
		}
		
	}*/


	public List<String> findPMIdsBySearch(String searchTerm) { //throws BibliographicApiException {
		if (searchTerm == null || searchTerm.isEmpty()) return Collections.emptyList(); 
		return this.buildSearchQuery(searchTerm).execute().get().getIds().collect(Collectors.toList());
	}

	public List<String> findPMIdsByDoi(String doi) { // throws BibliographicApiException {
		if (doi.isEmpty()) return Collections.emptyList();
		return this.buildSearchQuery(doi).execute().get().getIds().collect(Collectors.toList());
	}

	public List<String> findPMIdsByPubMedCentralIds(String pmcid) { //throws BibliographicApiException {
		if (pmcid.isEmpty()) return Collections.emptyList();
		return this.buildSearchQuery("PMC"+pmcid.replace("PMC", "")).execute().get().getIds().collect(Collectors.toList());
	}
	
	private String keyFrom(String pmid) {
		MultivaluedMap<String, String> fetchParams = defaultApiParams();
		fetchParams.add("db", "pubmed");
		fetchParams.add("id",pmid);
		fetchParams.add("format", "xml");
		return keyFromApiQuery(EFETCH,fetchParams);
	}
	
	/**
	 * Fetch PubMed article metadata and abstract
	 * 
	 * @param pmid
	 * @return
	 * @throws BibliographicApiException 
	 * @throws JAXBException
	 */
	public Set<EntrezEntry> getPMEntriesByPMIds(Collection<String> pmids) { // throws BibliographicApiException {
		Set<EntrezEntry> out = new HashSet<>();
		if (pmids.isEmpty()) return out;
		Cache<String,BinaryData> cache = this.permanentCache(); 
		Collection<String> deferred = new HashSet<>();
		for (String pmid: pmids) {
			if (cache.containsKey(keyFrom(pmid))) {
				try {
					out.add(new EntrezEntry(Xml.fromStream(cache.get(keyFrom(pmid)).inputStream()).content()));
					} catch (XmlException e) {
						logger.debug("error parsing cached content for: "+pmid);
						cache.remove(keyFrom(pmid));
						deferred.add(pmid);
					}
			} else {
				deferred.add(pmid);
			}
		}
		if (!deferred.isEmpty()) {
			MultivaluedMap<String, String> fetchParams = defaultApiParams();
			fetchParams.add("db", "pubmed");
			deferred.forEach(id -> fetchParams.add("id",id));
			fetchParams.add("format", "xml");
			rateLimit();
			logger.debug("making efetch query for {} pubmed records with params {}", deferred.size(), fetchParams.toString());
			Optional<InputStream> is = this.buildCall(EFETCH, InputStream.class)
					.withParams(fetchParams)
					.withOperation(is2 -> is2)
					.post();
			if (!is.isPresent()) {
				logger.warn("Could not fetch content from entrez. Returning items from cache");
				return out; //Fetch failed but there was cached content.
			}
			Xml xml;
			try {
				xml = Xml.fromStream(is.get());
				EntrezEntries tmp = new EntrezEntries(xml.content());
				tmp.stream().forEach(entry -> {
					out.add(entry);
					String pmid = entry.getPMID().get();
					cache.put(keyFrom(pmid), BinaryData.from(entry.getRaw().outerXml()));
				});
			} catch (XmlException e) {
				throw new BibliographicApiException("could not parse result",e);
			}
		}
		return out;
	}
	
	public Optional<EntrezEntries> getPMEntriesByWebEnvAndQueryKey(String webEnv, String queryKey) {
		MultivaluedMap<String, String> fetchParams = defaultApiParams();
		fetchParams.add("db", "pubmed");
		fetchParams.add("format", "xml");
		fetchParams.add("query_key", queryKey);
		fetchParams.add("WebEnv", webEnv);
		return this.buildCall(EFETCH, EntrezEntries.class)
			.withParams(fetchParams)
			.withOperation(is -> {
				Xml xml = Xml.fromStream(is);
				return new EntrezEntries(xml.content());
			}).post();
	}

	public Optional<EntrezEntry> getPMEntryByPMId(String pmid) { //throws BibliographicApiException {
		if (pmid == null || pmid.isEmpty()) return Optional.empty(); 
		return getPMEntriesByPMIds(Collections.singletonList(pmid)).stream().findFirst();
	}

	/**
	 * retrieves a full text for an article from PubMed Central
	 * @param pmcId
	 * @return
	 * @throws JAXBException
	 */
	public Optional<InputStream> getPubMedCentralXMLByPubMedCentralId(String pmcId) {
		if (pmcId == null || pmcId.isEmpty()) return Optional.empty();
		return getXMLByIdsAndDatabase(Collections.singletonList(pmcId), Database.PMC);
	}


	public Optional<InputStream> getPubMedCentralXMLByPMEntries(EntrezEntries pmEntries) {
		List<String> pmcIds = pmEntries.stream().flatMap(e -> e.getPMCID().stream()).collect(Collectors.toList());
		return getXMLByIdsAndDatabase(pmcIds, Database.PMC);
	}

	public Optional<InputStream> getPubMedCentralXMLByPMEntry(EntrezEntry pmEntry) {
		Optional<String> pmcId = pmEntry.getPMCID();
		return pmcId.flatMap(p -> getPubMedCentralXMLByPubMedCentralId(p));
	}

	public Optional<InputStream> getPubMedCentralPdfByPMEntry(EntrezEntry pmEntry, PdfFetcher pdfFetch) {
		if (pmEntry.getPdfUri().isPresent()) {
			String pdfUrl = pmEntry.getPdfUri().get().toString();
			return pdfFetch.getPdfFromUrl(pdfUrl);
		} else {
			return Optional.empty();
		}
	}
	
	public Optional<InputStream> getPubMedCentralPdfByPMEntry(EntrezEntry pmEntry) throws BibliographicApiException {
		return getPubMedCentralPdfByPMEntry(pmEntry, PdfFetcher.create());
	}
	
	/**
	 * retrieves a full entries for a list of articles from the given database 
	 * @param list of ids
	 * @return
	 */
	public Optional<InputStream> getXMLByIdsAndDatabase(Collection<String> ids,Database db) {
		MultivaluedMap<String, String> params = defaultApiParams();
		params.add("db", db.name().toLowerCase());
		params.add("retmode", "xml");
		params.add("id", ids.stream().collect(Collectors.joining(",")));
		logger.debug("making efetch query with params {}", params.toString());
		return buildCall(EFETCH,InputStream.class)
			.withParams(params)
			.withOperation(is -> is)
			.post();
	}


	/**
	 * Elinks
	 * @return
	 */
	public ELinksQueryBuilder buildLinksQueryForIdsAndDatabase(Collection<String> ids, Database fromDb) {
		return new ELinksQueryBuilder(defaultApiParams(),ids, fromDb, this);
	}

	/* The history mechanism doesn't really work due to a bug in Entrez which reformats the call slightly
	 * public ELinksQueryBuilder buildLinksQueryForSearchResult(Search search, Database fromDb) {
		return new ELinksQueryBuilder(defaultApiParams(), search.getWebEnv().get(), search.getQueryKey().get(),fromDb, this);
	}*/
	
	public static class ELinksQueryBuilder {
		MultivaluedMap<String, String> searchParams;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		boolean empty = false;
		EntrezClient client;

		@Deprecated
		protected WebResource get(WebResource searchService) {
			WebResource tdmCopy = searchService;
			return tdmCopy.queryParams(searchParams);
		}

		protected <X> X post(WebResource searchService, Class<X> output) {
			WebResource tdmCopy = searchService;
			return tdmCopy.post(output,searchParams);
		}
		
		//TODO: use putSingle instead of add
		protected ELinksQueryBuilder(MultivaluedMap<String, String> searchParams, Collection<String> ids, Database fromDb, EntrezClient client) {
			this.searchParams = searchParams;
			this.searchParams.add("dbfrom", fromDb.name().toLowerCase());
			this.searchParams.add("cmd", "neighbour_score");
			this.searchParams.add("retmode", "xml");
			this.searchParams.add("retmax", "100000");
			ids.forEach(id -> this.searchParams.add("id", id));
			if (ids.isEmpty()) empty=true;
			this.client = client;
		}
		
		protected ELinksQueryBuilder(MultivaluedMap<String, String> searchParams, String webEnv, String queryKey, Database fromDb, EntrezClient client) {
			this.searchParams = searchParams;
			this.searchParams.add("dbfrom", fromDb.name().toLowerCase());
			this.searchParams.add("cmd", "neighbour_score");
			this.searchParams.add("retmode", "xml");
			this.searchParams.add("WebEnv", webEnv);
			this.searchParams.add("retmax", "100000");
			this.searchParams.add("query_key", queryKey);
			this.client = client;
		}

		public ELinksQueryBuilder command(Command command) {
			searchParams.remove("cmd");
			this.searchParams.add("cmd", command.name().toLowerCase());
			return this;
		}

		public ELinksQueryBuilder toDatabase(Database to) {
			searchParams.remove("db");
			this.searchParams.add("db", to.name().toLowerCase());
			return this;
		}

		public ELinksQueryBuilder withLinkname(String linkName) {
			this.searchParams.putSingle("linkname", linkName);
			return this;
		}

		public ELinksQueryBuilder searchLinked(String term) {
			this.searchParams.putSingle("term", term);
			return this;
		}

		public ELinksQueryBuilder withinLastDays(int count) {
			searchParams.remove("reldate");
			searchParams.remove("datetype");
			this.searchParams.add("datetype", "edat");
			this.searchParams.add("reldate", Integer.toString(count));
			return this;
		}

		public ELinksQueryBuilder betweenDates(LocalDate start, LocalDate end) {
			searchParams.remove("mindate");
			searchParams.remove("maxdate");
			searchParams.remove("datetype");
			this.searchParams.add("datetype", "edat");
			this.searchParams.add("mindate", start.format(formatter));
			this.searchParams.add("maxdate", end.format(formatter));
			return this;
		}

		public Optional<EntrezLinks> execute() {
			if (empty) return Optional.empty();
			return client.buildCall(ELINK, EntrezLinks.class)
				.withParams(searchParams)
				.withOperation(is -> {
					Xml resp = Xml.fromStream(is);
					return new EntrezLinks(resp.content());
				}).post();
		}
		
		public String toString() {return searchParams.toString();}
	}

	public static enum Command {
		NEIGHBOR, NEIGHBOR_SCORE, PRLINKS, LLINKS
	}


	/*public EntrezResult.Links link(ELinksQueryBuilder builder) throws BibliographicApiException {
		logger.debug("making elink query with params {}", builder.toString());
		rateLimiter.consume();
		InputStream is = builder.post(eLinkResource, InputStream.class);
		try {
			
			Xml resp = Xml.fromStream(is);
			is.close();
			return new EntrezResult.Links(resp.content());
			
		} catch (XmlException | IOException e1) {
			throw new BibliographicApiException("could not parse result",e1);
		} 
		
	}*/

	public Map<String,Long> getSimilarScoredPMIdsByPMId(String pmid) throws BibliographicApiException {
		Map<String,Long> out = new HashMap<>();
		this.buildLinksQueryForIdsAndDatabase(Collections.singletonList(pmid), Database.PUBMED)
			.command(Command.NEIGHBOR_SCORE)
			.withLinkname("pubmed_pubmed")
			.execute().stream()
			.flatMap(o -> o.stream())
			.forEach(l -> {
				l.toId.ifPresent(to -> out.put(to, l.score.orElse(0L)));
			});
		return out;
	}

	public Map<String,String> getPubMedCentralIdsByPMId(Collection<String> pmids) throws BibliographicApiException {
		Map<String,String> out = new HashMap<>();
		this.buildLinksQueryForIdsAndDatabase(pmids, Database.PUBMED)
				.toDatabase(Database.PMC)
				.command(Command.NEIGHBOR)
				.withLinkname("pubmed_pmc")
				.execute().stream()
				.flatMap(o -> o.stream())
				.forEach(l -> l.toId.ifPresent(to -> out.put(l.fromId, to)));
		return out;
	}

	public Map<String,String> getReferencedPMIdsByPMId(Collection<String> pmids) throws BibliographicApiException {
		Map<String,String> out = new HashMap<>();
		this.buildLinksQueryForIdsAndDatabase(pmids, Database.PUBMED)
				.toDatabase(Database.PUBMED)
				.command(Command.NEIGHBOR)
				.withLinkname("pubmed_pubmed_refs")
				.execute().stream()
				.flatMap(o -> o.stream())
				.forEach(l -> l.toId.ifPresent(to -> out.put(l.fromId, to)));
		return out;
	}

	public List<String> getReferencingPubMedCentralIdsByPubMedCentralId(String pmcId) throws BibliographicApiException {
		return this.buildLinksQueryForIdsAndDatabase(Collections.singletonList(pmcId), Database.PMC)
				.toDatabase(Database.PMC)
				.command(Command.NEIGHBOR)
				.withLinkname("pmc_pmc_citedby")
				.execute().stream()
				.flatMap(o -> o.stream())
				.flatMap(l -> l.toId.stream())
				.collect(Collectors.toList());
	}

	public List<String> getReferencedPubMedCentralIdsByPubMedCentralId(String pmcId) throws BibliographicApiException {
		return this.buildLinksQueryForIdsAndDatabase(Collections.singletonList(pmcId), Database.PMC)
				.toDatabase(Database.PMC)
				.command(Command.NEIGHBOR)
				.withLinkname("pmc_pmc_cites")
				.execute().stream()
				.flatMap(o -> o.stream())
				.flatMap(l -> l.toId.stream())
				.collect(Collectors.toList());
	}

	@Override
	public Collection<? extends Record> search(String search, Optional<LocalDate> from, Optional<LocalDate> to, Optional<Integer> limit) {
		ESearchQueryBuilder tmp = this.buildSearchQuery(search);
		List<EntrezEntry> out = new ArrayList<>();
		from.ifPresent(s -> tmp.betweenDates(s, to.orElse(LocalDate.now())));
		limit.ifPresent(l -> tmp.limit(1, l));
		tmp.execute().ifPresent(s -> {
			s.getStoredResult(this).ifPresent(e -> {
				out.addAll(e.stream().collect(Collectors.toList()));
			});
		});
		return out;
	}

	@Override
	public Collection<? extends CitationLink> referencesCiting(Collection<RecordReference> ref) {
		List<CitationLink> out = new ArrayList<>();
		List<String> pmcids = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.PMCID)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		this.buildLinksQueryForIdsAndDatabase(pmcids, Database.PMC)
				.toDatabase(Database.PMC)
				.command(Command.NEIGHBOR)
				.withLinkname("pmc_pmc_citedby")
				.execute().stream().flatMap(l -> l.getCitations()).forEach(c -> out.add(c));
		return out;
	}

	@Override
	public Set<? extends CitationLink> citesReferences(Collection<RecordReference> ref) {
		Set<CitationLink> out = new HashSet<>();
		List<String> pmids = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.PMID)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		List<String> pmcids = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.PMCID)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		this.buildLinksQueryForIdsAndDatabase(pmids, Database.PUBMED)
				.toDatabase(Database.PUBMED)
				.command(Command.NEIGHBOR)
				.withLinkname("pubmed_pubmed_refs")
				.execute().stream().flatMap(l -> l.getCitations()).forEach(c -> out.add(c));
		this.buildLinksQueryForIdsAndDatabase(pmcids, Database.PMC)
				.toDatabase(Database.PMC)
				.command(Command.NEIGHBOR)
				.withLinkname("pmc_pmc_cites")
				.execute().stream().flatMap(l -> l.getCitations()).forEach(c -> out.add(c));
		return out;
	}

	@Override
	public Map<RecordIdentifier, EntrezEntry> getById(Collection<RecordReference> ref) {
		Map<RecordIdentifier, EntrezEntry> out = new HashMap<>();
		List<String> pmids = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.PMID)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		// List<String> pmcids = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.PMCID)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		getPMEntriesByPMIds(pmids).forEach(ee -> out.put(Builder.recordReference(ee), ee));
		return out;
	}

	@Override
	public Set<RecordIdentifierMapping> mappings(Collection<RecordReference> source) {
		Set<RecordIdentifierMapping> out = new HashSet<>();
		Map<RecordIdentifier, EntrezEntry> tmp = getById(source);
		for (Entry<RecordIdentifier, EntrezEntry> e: tmp.entrySet()) {
			Set<RecordIdentifier> allIds = new HashSet<>();
			allIds.add(e.getKey());
			allIds.add(Builder.recordReference(e.getValue()));
			e.getValue().getOtherIdentifiers().stream().map(Builder::recordReference).forEach(allIds::add);
			for (RecordIdentifier src: allIds) {
				for (RecordIdentifier targ: allIds) {
					//if (!src.equals(targ)) {
						out.add(Builder.recordIdMapping(src,targ));
					//}
				}
			}
		}
		return out;
	}
	
}