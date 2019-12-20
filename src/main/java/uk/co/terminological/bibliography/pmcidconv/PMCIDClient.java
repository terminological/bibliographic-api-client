package uk.co.terminological.bibliography.pmcidconv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;

import org.ehcache.Cache;
import org.ehcache.spi.loaderwriter.CacheLoadingException;
import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import uk.co.terminological.bibliography.BibliographicApiException;
import uk.co.terminological.bibliography.BinaryData;
import uk.co.terminological.bibliography.CachingApiClient;
import uk.co.terminological.bibliography.client.IdMapper;
import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.RecordIdentifier;
import uk.co.terminological.bibliography.record.RecordIdentifierMapping;
import uk.co.terminological.bibliography.record.RecordReference;

public class PMCIDClient extends CachingApiClient implements IdMapper {

	private static final Logger logger = LoggerFactory.getLogger(PMCIDClient.class);
	private static Map<String, PMCIDClient> instances = new HashMap<>();
	
	private String developerEmail;
	private String toolName;
	private static String URL = "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/";
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
	
	public static PMCIDClient create(String developerEmail, String toolName) {
		return new PMCIDClient(developerEmail,toolName, Optional.empty());
	}
	
	public static PMCIDClient create(String developerEmail, String toolName, Path cacheDir) {
		if (!instances.containsKey(developerEmail)) {
			instances.put(developerEmail, new PMCIDClient(developerEmail,toolName, Optional.ofNullable(cacheDir)));
		}
		return instances.get(developerEmail);	
	}
	
	private PMCIDClient(String developerEmail, String toolName, Optional<Path> cacheDir) {
		super ( cacheDir, TokenBuckets.builder().withInitialTokens(1000).withCapacity(1000).withFixedIntervalRefillStrategy(1000, 24*6*6, TimeUnit.SECONDS).build());
		this.developerEmail = developerEmail;
		this.toolName = toolName;
	}
	
	protected MultivaluedMap<String, String> defaultApiParams() {
		MultivaluedMap<String, String> out = new MultivaluedMapImpl();
		out.add("tool", toolName);
		out.add("email", developerEmail);
		out.add("format", "json");
		return out;
	}
	
	//These keys are for the individual components but format could collide with single query
	private String keyFrom(String id, IdType idType) {
		MultivaluedMap<String, String> params = defaultApiParams();
		params.add("ids", id);
		params.add("idtype", idType.name().toLowerCase());
		params.add("component", "result"); //Prevent a key collision
		return keyFromApiQuery(URL,params);
	}
	
	// Batches the calls to groups of max 50 ids
	public Set<PMCIDRecord> getMapping(Collection<String> id2, IdType idType) throws BibliographicApiException {
		Set<PMCIDRecord> out = new HashSet<>();
		Cache<String,BinaryData> cache = permanentCache();
		List<String> id = new ArrayList<String>();
		
		for (String nextId: id2) {
			if (cache.containsKey(keyFrom(nextId,idType))) {
				try {
					PMCIDRecord tmp = objectMapper.readValue(
							cache.get(keyFrom(nextId,idType)).inputStream(), 
							PMCIDRecord.class);
					out.add(tmp);
				} catch (CacheLoadingException | IOException e) {
					logger.debug("error parsing cached content for: {}, {}",nextId,idType);
					cache.remove(keyFrom(nextId,idType));
					id.add(nextId);
				} 
			} else {
				id.add(nextId);
			}
		}
		int start = 0;
		while (start<id.size()) {
			int end = id.size()<start+50 ? id.size() : start+50;
			List<String> tmp2 = id.subList(start, end);
			Optional<PMCIDResult> outTmp = doCall(tmp2,idType);
			outTmp.ifPresent(o -> {
				out.addAll(o.records);
				o.records.forEach(r -> {
					//Add component to cache at individual level
					BinaryData ser;
					try {
						ser = BinaryData.from(objectMapper.writeValueAsBytes(r));
						r.pmid.ifPresent(i -> cache.put(keyFrom(i,IdType.PMID), ser));
						r.doi.ifPresent(i -> cache.put(keyFrom(i,IdType.DOI), ser));
						r.pmcid.ifPresent(i -> cache.put(keyFrom(i,IdType.PMCID), ser));
					} catch (JsonProcessingException e) {
						logger.warn("Could not serialise id converter record to json");
					}
				});
			});
			start += 50;
		}
		return out;
	}
	
	private Optional<PMCIDResult> doCall(Collection<String> id, IdType idType) {
		MultivaluedMap<String, String> params = defaultApiParams();
		params.add("ids", id.stream().collect(Collectors.joining(",")));
		params.add("idtype", idType.name().toLowerCase());
		logger.debug("calling id converter with params: "+params);
		return this.buildCall(URL, PMCIDResult.class)
			.cacheForever()
			.withParams(params)
			.withOperation(is -> objectMapper.readValue(is, PMCIDResult.class))
			.get();
	}
	
	public Map<String,String> getDoisByIdAndType(Collection<String> ids, IdType type) throws BibliographicApiException {
		Set<PMCIDRecord> tmp = getMapping(ids, type);
		Map<String,String> out = new HashMap<>();
		for (PMCIDRecord r: tmp) {
			r.doi.ifPresent(d -> out.put(r.idByType(type).get(), d));
		}
		return out;
	}
	
	public Map<String,String> getPubMedCentralIdsByIdAndType(Collection<String> ids, IdType type) throws BibliographicApiException {
		Set<PMCIDRecord> tmp = getMapping(ids, type);
		Map<String,String> out = new HashMap<>();
		for (PMCIDRecord r: tmp) {
			r.pmcid.ifPresent(d -> out.put(r.idByType(type).get(), d));
		}
		return out;
	}
	
	public Map<String,String> getPMIdsByIdAndType(Collection<String> ids, IdType type) throws BibliographicApiException {
		Set<PMCIDRecord> tmp = getMapping(ids, type);
		Map<String,String> out = new HashMap<>();
		for (PMCIDRecord r: tmp) {
			r.pmid.ifPresent(d -> out.put(r.idByType(type).get(), d));
		}
		return out;
	}
	
	public static class MergeableList<X> extends ArrayList<X> {
		public static <Y> MergeableList<Y> of(Y item) {
			MergeableList<Y> out = new MergeableList<>();
			out.add(item);
			return out;
		}
		public MergeableList<X> merge(MergeableList<X> input) {
			this.addAll(input);
			return this;
		}
	}
	
	@Override
	public Set<RecordIdentifierMapping> mappings(Collection<RecordReference> source) {
		Set<RecordIdentifierMapping> out = new HashSet<>();
		Map<IdType,MergeableList<String>> idsByType = new HashMap<>();
		source.stream()
			.filter(rr -> rr.getIdentifier().isPresent())
			.forEach(rr -> idsByType.merge(
					rr.getIdentifierType(), 
					MergeableList.of(rr.getIdentifier().get()), 
					(l1,l2) -> l1.merge(l2)));
		for (Entry<IdType,MergeableList<String>> entry: idsByType.entrySet()) {
			doCall(entry.getValue(),entry.getKey()).stream().flatMap(r -> r.records.stream()).forEach(
				pmcr -> {
					Set<RecordIdentifier> allIds = new HashSet<>();
					pmcr.doi.ifPresent(d -> allIds.add(Builder.recordReference(IdType.DOI, d)));
					pmcr.pmcid.ifPresent(d -> allIds.add(Builder.recordReference(IdType.PMCID, d)));
					pmcr.pmid.ifPresent(d -> allIds.add(Builder.recordReference(IdType.PMID, d)));
					for (RecordIdentifier src: allIds) {
						for (RecordIdentifier targ: allIds) {
							//if (!src.equals(targ)) {
								out.add(Builder.recordIdMapping(src,targ));
							//}
						}
					}
				}
			);
		}
		
		return out;
	}

	

	
}
