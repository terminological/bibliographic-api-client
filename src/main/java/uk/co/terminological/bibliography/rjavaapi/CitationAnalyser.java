package uk.co.terminological.bibliography.rjavaapi;

import java.util.UUID;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import uk.co.terminological.bibliography.record.MergedRecord;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.jsr223.RClass;
import uk.co.terminological.jsr223.RMethod;
import uk.co.terminological.jsr223.ROutput.Dataframe;

import static uk.co.terminological.jsr223.ROutput.mapping;
import static uk.co.terminological.jsr223.ROutput.toDataframe;

@RClass
public class CitationAnalyser {

	FluentMap<UUID, MergedRecord> records = new FluentMap<>();
	Graph<UUID, DefaultEdge> citations = new SimpleDirectedGraph<>(DefaultEdge.class);
	Repository repo;
	
	@SuppressWarnings("unchecked")
	@RMethod
	public CitationAnalyser(Repository repo) {
		this.repo = repo;
		repo.uuidCitations().forEach(t -> {
			this.citations.addVertex(t.getFirst());
			this.citations.addVertex(t.getSecond());
			this.citations.addEdge(t.getFirst(), t.getSecond());
		});
		this.records = (FluentMap<UUID, MergedRecord>) repo.records.clone();
	}
	
	@RMethod
	public Dataframe pageRank() {
		PageRank<UUID, DefaultEdge> pr = new PageRank<>(citations);
		return pr.getScores().entrySet().stream().collect(toDataframe(
			mapping(Cols.UUID, e -> e.getKey()),
			mapping(Cols.PAGERANK, e -> e.getValue())
		));
	}
	
}
