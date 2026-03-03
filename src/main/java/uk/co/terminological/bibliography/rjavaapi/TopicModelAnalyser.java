package uk.co.terminological.bibliography.rjavaapi;

import static uk.co.terminological.rjava.RConverter.dataframeCollector;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.nlptools.Corpus;
import uk.co.terminological.nlptools.Document;
import uk.co.terminological.nlptools.Filter;
import uk.co.terminological.nlptools.TopicModelBuilder;
import uk.co.terminological.nlptools.TopicModelResult;
import uk.co.terminological.rjava.RClass;
import uk.co.terminological.rjava.RMethod;
import uk.co.terminological.rjava.types.RCharacterVector;
import uk.co.terminological.rjava.types.RDataframe;


@RClass
public class TopicModelAnalyser {

	transient Repository repo;
	TopicModelResult result;
	List<String> textStopwords;
	
	@RMethod
	public TopicModelAnalyser(Repository repo, RCharacterVector stopwords) {
		this.repo = repo;
		this.textStopwords = stopwords.get().collect(Collectors.toList());
		recalculate(10,0.10,0.1);
	}
	
	@RMethod
	public void save(String topicPath) {
		try {
			//Output oos = new Output(Files.newOutputStream(topicPath));
			//kryo.writeObject(oos,result);
			FileOutputStream fos = new FileOutputStream(topicPath);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@RMethod
	public TopicModelAnalyser setStopwords(RCharacterVector stopwords) {
		this.textStopwords = stopwords.get().collect(Collectors.toList());
		return this;
	}
	
	@RMethod
	public TopicModelAnalyser recalculate(int topics, double alpha, double beta) {
		// Kryo kryo = new Kryo();
	    // kryo.register(TopicModelResult.class);
		result = null;
		Corpus texts = Corpus.create()
				.withStopwordFilter(textStopwords)
				.withTokenFilter(Filter.number())
				.withTokenFilter(Filter.shorterThan(4));

		repo.records.forEach((k,r) -> {
			
			//Integer next = r.get("community").asInt();
			//Integer artComm = r.get("articleCommunity").asInt();
			
			String nodeId = k.toString();
			String title = r.getTitle().orElse("");
			String abstrct = r.getAbstract().orElse("");
			Document doc = texts.addDocument(nodeId, title+(abstrct != null ? "\n"+abstrct : ""));
			
			//doc.addMetadata("community",next);
			//doc.addMetadata("articleCommunity",artComm);
			
			r.getIdentifier(IdType.DOI).ifPresent(d -> doc.addMetadata(Cols.DOI,d));
			r.getIdentifier(IdType.PMCID).ifPresent(d -> doc.addMetadata(Cols.PMC,d));
			r.getIdentifier(IdType.PMID).ifPresent(d -> doc.addMetadata(Cols.PMID,d));
			
			
		});
		

		result = TopicModelBuilder.create(texts).withTopics(topics).execute(alpha,beta);
		return this;
	}
	
	@RMethod
	public RDataframe getTopicsForDocuments() {
		return this.result.getTopicsForDocuments().flatMap(t -> {
			return t.streamDocuments().map(wd -> {
				return FluentMap.create(String.class, Object.class)
					.and(Cols.TOPIC, t.getTopicId())
					.and(Cols.UUID, wd.getTarget().getIdentifier())
					.and(Cols.SCORE,wd.getWeight());
			});
		}).collect(dataframeCollector());
				
	}
	
}
