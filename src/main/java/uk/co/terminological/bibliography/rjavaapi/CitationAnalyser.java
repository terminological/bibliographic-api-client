package uk.co.terminological.bibliography.rjavaapi;

import static uk.co.terminological.rjava.RConverter.mapping;
import static uk.co.terminological.rjava.RConverter.dataframeCollector;

import java.util.UUID;

import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import uk.co.terminological.bibliography.record.MergedRecord;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.rjava.RClass;
import uk.co.terminological.rjava.RMethod;
import uk.co.terminological.rjava.types.RDataframe;


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
			if (!t.getFirst().equals(t.getSecond())) {
				this.citations.addVertex(t.getSecond());
				this.citations.addEdge(t.getFirst(), t.getSecond());
			}
		});
		this.records = (FluentMap<UUID, MergedRecord>) repo.records.clone();
	}
	
	@RMethod
	public RDataframe pageRank() {
		PageRank<UUID, DefaultEdge> pr = new PageRank<>(citations);
		return pr.getScores().entrySet().stream().collect(dataframeCollector(
			mapping(Cols.UUID, e -> e.getKey().toString()),
			mapping(Cols.PAGERANK, e -> e.getValue())
		));
	}
	
}
