package uk.co.terminological.bibliography.rjavaapi;

import static uk.co.terminological.jsr223.ROutput.mapping;
import static uk.co.terminological.jsr223.ROutput.toDataframe;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.terminological.bibliography.record.Builder;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.ImmutableRecordReference;
import uk.co.terminological.bibliography.record.MergedRecord;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.RecordReferenceMapping;
import uk.co.terminological.bibliography.record.RecordWithCitations;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.datatypes.FluentSet;
import uk.co.terminological.datatypes.Tuple;
import uk.co.terminological.jsr223.RClass;
import uk.co.terminological.jsr223.RInput;
import uk.co.terminological.jsr223.RMethod;
import uk.co.terminological.jsr223.ROutput.Dataframe;

/**
 * noramlise DOI 
 * manages a cross map of ids fo different types
 * retains and resolves record references to individual article records,
 * merges and deduplicates
 * resolves author references to authors
 * resolves institutions to other institutions.
 * resolves citations to canonical citations
 * resolve mesh code links
 * manage and cache citation strings
 * 
 * @author terminological
 *
 */
@RClass
public class Repository implements Serializable {
	//TODO: Save and load repository on bibliographic API startup and shutdown
	//TODO: Use repository in API clients, to resolve references.
	//TODO: Methods to navigate repository
	//TODO: Is the repository a graph database? Is it a database?
	//TODO: Consider loading 217MB ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/ for id mapping
	//TODO: Consider JGraphT as in memory graph for determining expansion / shortest path etc
	
	static Logger log = LoggerFactory.getLogger(Repository.class);
	
	transient private ApiClient api;
	FluentMap<UUID, MergedRecord> records = new FluentMap<>();
	FluentMap<ImmutableRecordReference, UUID> ids = new FluentMap<>();
	Graph<ImmutableRecordReference, DefaultEdge> citations = new SimpleDirectedGraph<>(DefaultEdge.class);
	
	Set<ImmutableRecordReference> expandedUp = new HashSet<>();
	Set<ImmutableRecordReference> expandedDown = new HashSet<>();
	Set<ImmutableRecordReference> mapped = new HashSet<>();
	//Graph<UUID, DefaultEdge> uuidCites = null;
	
	@RMethod
	public Repository(ApiClient api) {
		this.api = api;
	}
	
	protected void setApi(ApiClient api) {
		this.api = api;
	}
	
	public void addAll(Collection<? extends Record> records) {
		records.forEach(this::add);
	}
	
	protected void add(Record record) {
		
		UUID uuid = merge(record.getIdentifiers());
		Optional<MergedRecord> toMerge = records.getOpt(uuid);
		MergedRecord mr = toMerge.map(m -> m.merge(record)).orElse(MergedRecord.from(record));
		records.put(uuid, mr);
		// get a
		// regardless of uuid stuff the bare ids can go in the citation graph. Duplicates will be resolved
		record.getIdentifiers().forEach(r -> {
			citations.addVertex(r);
		});
		
		if (record instanceof RecordWithCitations) {
			((RecordWithCitations) record).getCitations().forEach(this::add);
			//Builder.recordReference(record).ifComplete(expandedDown::add);
		}
		
	}
	
	protected void add(CitationLink citationLink) {
		if(!citationLink.isComplete()) return;
		ImmutableRecordReference source = citationLink.getSource().getIdentifier().map(Builder::recordReference).get();
		ImmutableRecordReference target = citationLink.getTarget().getIdentifier().map(Builder::recordReference).get();
		add(source);
		add(target);
		citations.addEdge(source, target);
	}
	
	protected UUID merge(Collection<ImmutableRecordReference> ri) {
		List<UUID> uuids = ri.stream().flatMap(ids::getStream).collect(Collectors.toList());
		List<MergedRecord> toMerge = uuids.stream().flatMap(records::getStream).collect(Collectors.toList());
		if (uuids.size() != 1) {
			Optional<MergedRecord> merged = toMerge.stream().collect(MergedRecord.collector());
			UUID newUuid = UUID.randomUUID();
			ri.forEach(i -> ids.put(i, newUuid));
			uuids.forEach(records::remove);
			merged.ifPresent(m -> records.put(newUuid, m));
			return newUuid;
		} else {
			ri.forEach(i -> ids.put(i, uuids.get(0)));
			return uuids.get(0);
		}
		
	}
	
	protected void add(RecordReferenceMapping mapping) {
		if(!mapping.isComplete()) return;
		ImmutableRecordReference sourceRR = Builder.recordReference(mapping.getSource());
		ImmutableRecordReference targetRR = Builder.recordReference(mapping.getTarget());
		UUID source = add(sourceRR);
		UUID target = add(targetRR);
		if (source.equals(target)) {
			//both present and matching UUIDs
		} else {
			merge(List.of(
					sourceRR,
					targetRR
			));//both present and not matching. This needs a merge operation
		}
	}
	
	protected UUID add(ImmutableRecordReference ri) {
		if (!ids.containsKey(ri)) {
			ids.put(ri, UUID.randomUUID());
			citations.addVertex(ri);
		}
		UUID uuid = ids.get(ri);
		return(uuid);
	}
	
	protected void remove(ImmutableRecordReference ri) {
		Optional<UUID> uuid = ids.getOpt(ri);
		
		uuid.ifPresent(u -> {
			records.remove(u);
			List<ImmutableRecordReference> allRi = ids.entrySet().stream()
					.filter(kv -> kv.getValue().equals(u)).map(kv -> kv.getKey())
					.collect(Collectors.toList());
			allRi.forEach(ri2 -> {
				ids.remove(ri2);
				citations.removeVertex(ri2);
			});
		});
		
	}
	
	
	private Set<ImmutableRecordReference> retrievedIds() {
		return ids.entrySet().stream()
				.filter(kv -> records.containsKey(kv.getValue()))
				.map(kv -> kv.getKey())
				.collect(Collectors.toSet());
	}
	
	private Set<ImmutableRecordReference> missingIds() {
		return ids.entrySet().stream()
			.filter(kv -> !records.containsKey(kv.getValue()))
			.map(kv -> kv.getKey())
			.collect(Collectors.toSet());
	}
	
	private HashMap<UUID,Set<ImmutableRecordReference>> uuidToRef() {
		FluentMap<UUID,Set<ImmutableRecordReference>> out = new FluentMap<>();
		for (Entry<ImmutableRecordReference,UUID> kv: this.ids.entrySet()) {
			if(!out.containsKey(kv.getValue())) out.put(kv.getValue(), FluentSet.empty());
			out.get(kv.getValue()).add(kv.getKey());
		}
		return out;
	}
	
	
	// R methods
	
	@RMethod
	public Repository withCitationStyle(String citationStyle) {
		this.api.withCitationStyle(citationStyle);
		return(this);
	}
	
	
	@RMethod
	public CitationAnalyser analyseCitations() {
		return new CitationAnalyser(this);
	}
	
	@RMethod
	public TopicModelAnalyser topicModeller(List<String> stopwords) {
		return new TopicModelAnalyser(this,stopwords);
	}
	
	protected Stream<Tuple<UUID,UUID>> uuidCitations() {
		return citations
				.edgeSet()
				.stream()
				.map(e -> Tuple.create(
						ids.get(citations.getEdgeSource(e)),
						ids.get(citations.getEdgeTarget(e)))
				)
				.distinct();
	}
	
	@RMethod
	public Dataframe getUuidCitations() {
		return uuidCitations()
			.collect(
				toDataframe(
					mapping(Tuple.class,Cols.SOURCE_UUID, s-> s.getFirst()),
					mapping(Tuple.class,Cols.TARGET_UUID, s-> s.getSecond())
				)
			);
	}
	
	@RMethod
	public Dataframe getRecords() {
		return 
			records.entrySet().stream()
			.collect(toDataframe(
					mapping(Entry.class,Cols.UUID, s-> s.getKey()),
					mapping(Entry.class,Cols.TITLE, s-> ((Record) s.getValue()).getTitle().orElse(null)),
					mapping(Entry.class,Cols.ABSTRACT, s-> ((Record) s.getValue()).getAbstract().orElse(null)),
					mapping(Entry.class,Cols.CITATION, s-> ((Record) s.getValue()).render(api.citationStyle).orElse(null)),
					mapping(Entry.class,Cols.PMID, s-> ((Record) s.getValue()).getIdentifier(IdType.PMID).orElse(null)),
					mapping(Entry.class,Cols.DOI, s-> ((Record) s.getValue()).getIdentifier(IdType.DOI).orElse(null)),
					mapping(Entry.class,Cols.PMC, s-> ((Record) s.getValue()).getIdentifier(IdType.PMCID).orElse(null)),
					mapping(Entry.class,Cols.ID, s -> ((Record) s.getValue()).getIdentifier().orElse(null)),
					mapping(Entry.class,Cols.ID_TYPE, s -> ((Record) s.getValue()).getIdentifierType().toString()),
					mapping(Entry.class,Cols.URL, s -> ((Record) s.getValue()).getPdfUri().orElse(null)),
					mapping(Entry.class,Cols.DATE, s -> ((Record) s.getValue()).getDate().orElse(null))
					));
	}
	
	@RMethod
	public Dataframe getCitations() {
		return 
			citations
			.edgeSet().stream()
			.map(e -> Tuple.create(citations.getEdgeSource(e), citations.getEdgeTarget(e)))
			.collect(toDataframe(
					mapping(Tuple.class, Cols.SOURCE_ID, t -> ((ImmutableRecordReference) t.getFirst()).getIdentifier().orElse(null)),
					mapping(Tuple.class, Cols.SOURCE_ID_TYPE, t -> ((ImmutableRecordReference) t.getFirst()).getIdentifierType().toString()),
					mapping(Tuple.class, Cols.SOURCE_UUID, t -> ids.getOpt(((ImmutableRecordReference) t.getFirst())).orElse(null)),
					mapping(Tuple.class, Cols.SOURCE_TITLE, t -> ids.getOpt(((ImmutableRecordReference) t.getFirst())).flatMap(u -> records.getOpt(u)).flatMap(r -> r.getTitle()).orElse(null)),
					mapping(Tuple.class, Cols.TARGET_ID, t -> ((ImmutableRecordReference) t.getSecond()).getIdentifier().orElse(null)),
					mapping(Tuple.class, Cols.TARGET_ID_TYPE, t -> ((ImmutableRecordReference) t.getSecond()).getIdentifierType().toString()),
					mapping(Tuple.class, Cols.TARGET_UUID, t -> ids.getOpt(((ImmutableRecordReference) t.getSecond())).orElse(null)),
					mapping(Tuple.class, Cols.TARGET_TITLE, t -> ids.getOpt(((ImmutableRecordReference) t.getSecond())).flatMap(u -> records.getOpt(u)).flatMap(r -> r.getTitle()).orElse(null))
			));
//			.map(e -> {
//				CitationReference s = Builder.citationReference(Optional.of(
//						citations.getEdgeSource(e)), 
//						ids.getOpt(citations.getEdgeSource(e)).flatMap(u -> records.getOpt(u)).flatMap(r -> r.getTitle()),
//						Optional.empty());
//				CitationReference t = Builder.citationReference(
//						Optional.of(citations.getEdgeTarget(e)), 
//						ids.getOpt(citations.getEdgeTarget(e)).flatMap(u -> records.getOpt(u)).flatMap(r -> r.getTitle()), 
//						Optional.empty());
//				return Builder.citationLink(s,t,Optional.empty());
//			})
//			.collect(api.citationLinkCollector());
	}
	
	@RMethod
	public Dataframe getIdMapping() {
		return ids.entrySet().stream()
				.collect(toDataframe(
						mapping(Entry.class,Cols.ID, s-> ((ImmutableRecordReference) s.getKey()).getIdentifier().get()),
						mapping(Entry.class,Cols.ID_TYPE, s-> ((ImmutableRecordReference) s.getKey()).getIdentifierType()),
						mapping(Entry.class,Cols.UUID, s-> s.getValue())
						));
	}
	
	
	@RMethod
	public Repository collectSearch(String search) {
		Collection<? extends Record> hits = api.search(search);
		hits.forEach(this::add);
		hits.stream()
			.filter(h -> h instanceof RecordWithCitations)
			.map(h -> (RecordWithCitations) h)
			.flatMap(h -> h.getCitations())
			.forEach(this::add);
		return this;
	}
	
	@RMethod
	public Repository collectSearchByApi(Map<String,String> searchByApi) {
		Collection<? extends Record> hits = api.multiSearch(searchByApi);
		hits.forEach(this::add);
		hits.stream()
			.filter(h -> h instanceof RecordWithCitations)
			.map(h -> (RecordWithCitations) h)
			.flatMap(h -> h.getCitations())
			.forEach(this::add);
		return this;
	}

	
	@RMethod
	public Repository fetchMissingRecords() {
		api.fetch(missingIds()).values().stream().forEach(this::add);
		return this;
	}
	
	@RMethod
	public Repository fetchRecords(List<Map<String,Object>> recordDf) {
		Set<ImmutableRecordReference> ids = ApiClient.idsFromDataframe(recordDf);
		api.fetch(ids).values().forEach(this::add);
		return this;
	}
	
	@RMethod
	public Repository expandDownCitationGraph(int limit) {
		Set<ImmutableRecordReference> toExpand = retrievedIds();
		toExpand.removeAll(expandedDown);
		if (toExpand.size() > limit) {
			log.error("Exceeded limit size for expansion: was {}",toExpand.size());
		} else {
			api.citesReferences(toExpand).stream().forEach(this::add);
			expandedDown.addAll(toExpand);
		}
		return this;
	}
	
	@RMethod
	public Repository expandUpCitationGraph(int limit) {
		Set<ImmutableRecordReference> toExpand = retrievedIds();
		toExpand.removeAll(expandedUp);
		if (toExpand.size() > limit) {
			log.error("Exceeded limit size for expansion: was {}",toExpand.size());
		} else {
			api.referencesCiting(toExpand).stream().forEach(this::add);
			expandedUp.addAll(toExpand);
		}
		return this;
	}
	
	@RMethod
	public Repository expandDown(List<Map<String,Object>> recordDf, int limit) {
		Set<ImmutableRecordReference> toExpand = ApiClient.idsFromDataframe(recordDf);
		toExpand.removeAll(expandedDown);
		if (toExpand.size() > limit) {
			log.error("Exceeded limit size for expansion: was {}",toExpand.size());
		} else {
			api.citesReferences(toExpand).stream().forEach(this::add);
			expandedDown.addAll(toExpand);
		}
		return this;
	}
	
	@RMethod
	public Repository expandUp(List<Map<String,Object>> recordDf, int limit) {
		Set<ImmutableRecordReference> toExpand = ApiClient.idsFromDataframe(recordDf);
		toExpand.removeAll(expandedUp);
		if (toExpand.size() > limit) {
			log.error("Exceeded limit size for expansion: was {}",toExpand.size());
		} else {
			api.referencesCiting(toExpand).stream().forEach(this::add);
			expandedUp.addAll(toExpand);
		}
		return this;
	}
	
	@RMethod
	public Repository removeRecords(List<Map<String,Object>> recordDf) {
		ApiClient.idsFromDataframe(recordDf).forEach(this::remove);
		return this;
	}

	
	@RMethod
	public Repository findAlternateIds() {
		Set<ImmutableRecordReference> toExpand = new HashSet<>(citations.vertexSet());
		toExpand.removeAll(mapped);
		Set<RecordReferenceMapping> tmp = api.mappings(toExpand);
		tmp.forEach(this::add);
		log.debug("added {} identifiers for {} original ids", tmp.size(), toExpand.size());
		mapped.addAll(toExpand);
		return this;
	}
	
	@RMethod
	public ApiClient getClient() {
		return this.api;
	}
	
	
	
	@RMethod 
	public String status() {
		return String.format("Records: %s\nIds: %s (of which unique %s)\nCitation links: %s", 
				this.records.size(), 
				this.ids.size(), 
				this.uuidToRef().keySet().size(), 
				this.citations.edgeSet().size()
				);
	}
	
	@RMethod
	public Repository save(String filename) throws IOException {
		filename  = filename.replaceFirst("^~", System.getProperty("user.home"));
		FileOutputStream f = new FileOutputStream(filename);
	    ObjectOutput s = new ObjectOutputStream(f);
	    s.writeObject(this);
	    s.flush();
	    s.close();
	    return this;
	}

	@RMethod
	public Repository addSingle(IdType idType, String id) {
		api.fetch(List.of(Builder.recordReference(idType, id))).values().forEach(this::add);
		return this;
	}
}
