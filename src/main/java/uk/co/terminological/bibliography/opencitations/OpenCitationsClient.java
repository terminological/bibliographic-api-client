package uk.co.terminological.bibliography.opencitations;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.RecordReference;

public class OpenCitationsClient extends CachingApiClient implements CitedByMapper,CitesMapper {

	protected OpenCitationsClient(Optional<Path> optional) {
		super(optional, TokenBuckets.builder()
				.withCapacity(50)
				.withInitialTokens(50)
				.withFixedIntervalRefillStrategy(50, 1, TimeUnit.SECONDS).build());
		// There is no acceptable use policy specified but this seems reasonable.
	}

	static String baseUrl = "https://opencitations.net/index/api/v1/";
	static OpenCitationsClient singleton;
	
	private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected MultivaluedMap<String, String> defaultApiParams() {
		return new MultivaluedMapImpl();
	}	
	// https://opencitations.net/index/api/v1
	
	public static OpenCitationsClient create() {
		if (singleton==null) singleton = new OpenCitationsClient(Optional.empty());
		return singleton;
	}
	
	public static OpenCitationsClient create(Path cacheDir) {
		if (singleton==null) singleton = new OpenCitationsClient(Optional.ofNullable(cacheDir));
		return singleton;
	}
	
	public List<String> getReferencingDoisByDoi(String doi) {
		//https://w3id.org/oc/index/api/v1/citations/10.1002/adfm.201505328
		return this.buildQuery(Action.CITATIONS, doi).execute().stream()
				.flatMap(lr -> lr.getCitations()).flatMap(c -> c.getCitingDoi().stream())
				.collect(Collectors.toList())
		;
	}
	
	public List<String> getReferencedDoisByDoi(String doi) {
		//https://w3id.org/oc/index/api/v1/references/10.1186/1756-8722-6-59
		return this.buildQuery(Action.REFERENCES, doi).execute().stream()
				.flatMap(lr -> lr.getCitations()).flatMap(c -> c.getCitedDoi().stream())
				.collect(Collectors.toList())
		;
	}
	
	public QueryBuilder buildQuery(Action action, String doi) {
		QueryBuilder out = new QueryBuilder(baseUrl+action.name().toLowerCase(), doi, defaultApiParams() , this);
		return out;
	}
	
	public static enum Action {
		CITATIONS, REFERENCES
	}
	
	public static class QueryBuilder {
		protected String url;

		MultivaluedMap<String, String> params;
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		OpenCitationsClient client;

		/*ivate WebResource get(Client client) {
			WebResource tdmCopy = client.resource(url);
			tdmCopy = tdmCopy.queryParams(params);
			return tdmCopy;
		}*/

		private QueryBuilder(String url, String doi, MultivaluedMap<String, String> defaultParams, OpenCitationsClient client ) {
			this.url=url+"/"+encode(doi);
			this.params=defaultParams;
			this.client = client;

		}
		
		public QueryBuilder withExclusions(Fields... fields ) {
			this.params.put("exclude", Arrays.stream(fields).map(f -> f.toString().toLowerCase()).collect(Collectors.toList()));
			return this;
		}
		
		public QueryBuilder withFilter(Map<Fields,String> fieldMap ) {
			this.params.put("filter", fieldMap.entrySet().stream().map(f -> f.getKey().toString().toLowerCase()+":"+f.getValue()).collect(Collectors.toList()));
			return this;
		}
		
		public QueryBuilder withSortAscending(Fields field) {
			this.params.add("sort", "asc("+field.toString().toLowerCase()+")");
			return this;
		}
		
		public QueryBuilder withSortDescending(Fields field) {
			this.params.add("sort", "desc("+field.toString().toLowerCase()+")");
			return this;
		}
		
		//always get json and dont allow jsonp
		
		public String toString() {
			return keyFromApiQuery(url,params); 
		}
		
		public Optional<OpenCitationsListResult> execute() {
			return client.buildCall(url, OpenCitationsListResult.class)
					.withParams(params)
					.withOperation(is -> new OpenCitationsListResult(client.objectMapper.readTree(is))).get();
		}
	}
	
	public static enum Fields {
		oci, citing, cited, creation, timespan, journal_sc, author_sc;
	}

	@Override
	public Set<? extends CitationLink> citesReferences(Collection<RecordReference> ref) {
		Set<CitationLink> out = new HashSet<>();
		List<String> dois = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.DOI)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		dois.stream().forEach(
				doi -> this.buildQuery(Action.CITATIONS, doi).execute().stream().flatMap(lr -> lr.getCitations()).forEach(
						e -> out.add(e)
				)
		);
		return out;
	}

	@Override
	public Collection<? extends CitationLink> referencesCiting(Collection<RecordReference> ref) {
		List<CitationLink> out = new ArrayList<>();
		List<String> dois = ref.stream().filter(r -> r.getIdentifierType().equals(IdType.DOI)).flatMap(r -> r.getIdentifier().stream()).collect(Collectors.toList());
		dois.stream().forEach(
				doi -> this.buildQuery(Action.REFERENCES, doi).execute().stream().flatMap(lr -> lr.getCitations()).forEach(
						e -> out.add(e)
				)
		);
		return out;
	}
}
