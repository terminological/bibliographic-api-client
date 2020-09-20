package uk.co.terminological.bibliography.rjavaapi;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.jsr223.RClass;
import uk.co.terminological.jsr223.RMethod;
import uk.co.terminological.jsr223.ROutput;
import uk.co.terminological.jsr223.ROutput.Dataframe;
import uk.co.terminological.nlptools.Corpus;
import uk.co.terminological.nlptools.Document;
import uk.co.terminological.nlptools.Filter;
import uk.co.terminological.nlptools.TopicModelBuilder;
import uk.co.terminological.nlptools.TopicModelResult;

@RClass
public class TopicModelAnalyser {

	transient Repository repo;
	TopicModelResult result;
	List<String> textStopwords;
	
	@RMethod
	public TopicModelAnalyser(Repository repo, List<String> stopwords) {
		this.repo = repo;
		this.textStopwords = stopwords;
		recalculate();
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
	public TopicModelAnalyser setStopwords(List<String> stopwords) {
		this.textStopwords = stopwords;
		return this;
	}
	
	@RMethod
	public TopicModelAnalyser recalculate() {
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
			
			r.getIdentifier(IdType.DOI).ifPresent(d -> doc.addMetadata("doi",d));
			r.getIdentifier(IdType.PMCID).ifPresent(d -> doc.addMetadata(Cols.PMC,d));
			r.getIdentifier(IdType.PMID).ifPresent(d -> doc.addMetadata("doi",d));
			
			
		});
		

		result = TopicModelBuilder.create(texts).withTopics(10).execute(0.1,0.1);
		return this;
	}
	
	@RMethod
	public Dataframe getTopicsForDocuments() {
		return this.result.getTopicsForDocuments().flatMap(t -> {
			return t.streamDocuments().map(wd -> {
				return FluentMap.create(String.class, Object.class)
					.and(Cols.TOPIC, t.getTopicId())
					.and(Cols.UUID, wd.getTarget().getIdentifier())
					.and(Cols.SCORE,wd.getWeight());
			});
		}).collect(ROutput.mapsToDataframe());
				
	}
	
}
