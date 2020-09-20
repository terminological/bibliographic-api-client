package uk.co.terminological.pubmedclient;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.terminological.bibliography.BibliographicApiException;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.Record;
import uk.co.terminological.bibliography.record.ImmutableRecordReference;
import uk.co.terminological.bibliography.rjavaapi.ApiClient;
import uk.co.terminological.bibliography.rjavaapi.Repository;
import uk.co.terminological.datatypes.FluentList;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.jsr223.ROutput.Dataframe;

public class TestCombinedApi {

	public static void main(String[] args) throws IOException, BibliographicApiException, ParseException, ClassNotFoundException {
		
		BasicConfigurator.configure();
		org.apache.log4j.Logger.getRootLogger().setLevel(Level.DEBUG);
		Logger log = LoggerFactory.getLogger(TestCombinedApi.class);
		
		log.debug("Debugging");
		
		ApiClient api = ApiClient.createCached(System.getProperty("user.home")+"/Dropbox/secrets.prop", "/tmp/ehcache/test125");
		//api.disableCache();
		
		Dataframe df1 = api.searchApis("challen ai safety");
		System.out.println(df1);
		
		Dataframe df2 = api.citesReferences((List<Map<String,Object>>) FluentList.create((Map<String,Object>)FluentMap.create(String.class, Object.class).and("id", "30636200").and("idType", "PMID")));
		System.out.println(df2);
		
		
		Dataframe df3 = api.fetchDetails(df1.subList(0, 1));
		System.out.println(df3);
//		Collection<? extends Record> hits = api
//			.withSearchLimit(10)
//			//.withSearchApis(List.of("entrez"))
//			.withSearchDates("2016-06-01", "2017-06-01")
//			.search("machine learning");
//		
//		hits.forEach(r -> System.out.println(Record.print(r)));
//		
//		Set<ImmutableRecordReference> ids = hits.stream().flatMap(r -> r.getIdentifiers().stream()).collect(Collectors.toSet());
//		api.fetch(ids).forEach((k,v)-> {
//			System.out.println(k);
//			System.out.println(Record.print(v));
//		});
//		
//		System.out.println("CITES:");
//		
//		api.citesReferences(hits).stream().map(CitationLink::print).forEach(System.out::println);
//		
//		System.out.println("CITING:");
//		api.referencesCiting(hits).stream().map(CitationLink::print).forEach(System.out::println);
		
		
		System.out.println("REPOSITORY");
		Repository repo = api.getRepository();
		repo.addSingle(IdType.DOI, "10.1136/bmjqs-2018-008370");
		//repo.addAll(hits);
		repo.expandUpCitationGraph(1000);
		repo.expandDownCitationGraph(60);
		repo.fetchMissingRecords();
		
		System.out.println("RECORDS");
		System.out.println(repo.getRecords());
		System.out.println("CITATIONS");
		System.out.println(repo.getCitations());
		System.out.println("PAGERANK");
		System.out.println(repo.analyseCitations().pageRank());
		
		repo.save("/tmp/testRepo.ser");
		
		Repository repo2 = api.loadRepository("/tmp/testRepo.ser");
		System.out.println(repo2.status());
		
		
		System.exit(0);
	}

}
