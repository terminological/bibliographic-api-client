package uk.co.terminological.pubmedclient;

import java.io.IOException;
import java.text.ParseException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.terminological.bibliography.BibliographicApiException;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.rjavaapi.ApiClient;
import uk.co.terminological.bibliography.rjavaapi.Repository;
import uk.co.terminological.rjava.types.RDataframe;
import uk.co.terminological.rjava.types.RVector;


public class TestCombinedApi {

	public static void main(String[] args) throws IOException, BibliographicApiException, ParseException, ClassNotFoundException {
		
		BasicConfigurator.configure();
		org.apache.log4j.Logger.getRootLogger().setLevel(Level.DEBUG);
		Logger log = LoggerFactory.getLogger(TestCombinedApi.class);
		
		log.debug("Debugging");
		
		ApiClient api = ApiClient.createCached(System.getProperty("user.home")+"/Dropbox/secrets.prop", "/tmp/ehcache/test125");
		//api.disableCache();
		
		api.withSearchLimit(10);
		RDataframe df1 = api.searchApis("covid 19 database");
		System.out.println(df1);
		
		//System.exit(0);
		
		RDataframe df2 = api.citesReferences(
				RDataframe.create()
				.withCol("id", RVector.with("30636200"))
				.withCol("idType", RVector.with("PMID")));
		System.out.println(df2);
		
		
		RDataframe df3 = api.fetchDetails(df1.subset(0, 1));
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
