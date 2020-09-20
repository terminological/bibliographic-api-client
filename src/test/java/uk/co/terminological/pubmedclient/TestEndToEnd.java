package uk.co.terminological.pubmedclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.terminological.bibliography.BibliographicApiException;
import uk.co.terminological.bibliography.crossref.CrossRefClient;
import uk.co.terminological.bibliography.crossref.CrossRefWork;
import uk.co.terminological.bibliography.entrez.EntrezClient;
import uk.co.terminological.bibliography.entrez.EntrezSearch;
import uk.co.terminological.bibliography.pmcidconv.PMCIDClient;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.unpaywall.UnpaywallClient;

public class TestEndToEnd {

	public static void main(String[] args) throws IOException, BibliographicApiException, ParseException {
		
		BasicConfigurator.configure();
		org.apache.log4j.Logger.getRootLogger().setLevel(Level.TRACE);
		Logger log = LoggerFactory.getLogger(TestEndToEnd.class);
		
		log.debug("Debugging");
		
		Properties prop = System.getProperties();
		prop.load(Files.newInputStream(Paths.get(prop.getProperty("user.home"),"Dropbox/secrets.prop")));
		
		//String xrefToken = prop.getProperty("crossref.clickthroughtoken");
		String developerEmail = prop.getProperty("developer-email");
		String pubmedApiToken = prop.getProperty("pubmed-apikey");
		String appId = prop.getProperty("app-id");
		
		
		Path ehcache = Paths.get("/tmp/ehcache/test124");
		Files.createDirectories(ehcache.getParent());
		
		EntrezClient pubmed = EntrezClient.create(pubmedApiToken, appId, developerEmail,ehcache.resolve("pubmed"));
		PMCIDClient mapper = PMCIDClient.create(appId,developerEmail, ehcache.resolve("idconv"));
		CrossRefClient xref = CrossRefClient.create(developerEmail, ehcache.resolve("xref"));
		UnpaywallClient unpaywall = UnpaywallClient.create(developerEmail, ehcache.resolve("unpaywall"));
		
		EntrezSearch result = pubmed.buildSearchQuery("machine learning").limit(0, 50).betweenDates(
				LocalDate.of(2016, 01, 1), 
				LocalDate.of(2017, 01, 1)).execute().get();
		
		List<String> dois2 = pubmed.getPMEntriesByPMIds(result.getIds().collect(Collectors.toList())).stream().flatMap(pme -> pme.getDoi().map(o -> Stream.of(o)).orElse(Stream.empty())).collect(Collectors.toList());
		Map<String,String> dois = mapper.getDoisByIdAndType(result.getIds().collect(Collectors.toList()), IdType.PMID);

		List<String> tmp = new ArrayList<>(dois2);
		tmp.removeAll(dois.values());
		System.out.println("Extra dois found by pubmed");
		tmp.forEach(System.out::println);
		
		List<String> tmp2 = new ArrayList<>(dois.values());
		tmp2.removeAll(dois2);
		System.out.println("Extra dois found by id mapper");
		tmp2.forEach(System.out::println);
		
		
		System.out.println("Unpaywall entries");
		unpaywall.getUnpaywallByDois(dois2).stream()
			.forEach(res -> System.out.println(res.getIdentifier().orElse("?")+"\t"+res.getTitle()+"\t"+res.getPdfUri()));
		
		Stream<CrossRefWork> work = dois2.stream().flatMap(doi -> {
			return xref.getByDoi(doi).map(o -> Stream.of(o)).orElse(Stream.empty());
		})
				.map(sr -> sr.getWork());
		work.forEach(w -> System.out.println(w.getTitle()));
		//https://academic.oup.com/bioinformatics/article-pdf/33/6/863/25147932/btw768.pdf
		CrossRefWork work2 = xref.getByDoi("10.1093/bioinformatics/btw768").get().getWork();
		//System.out.println(work.journalAbstract.get());
		work2.getReferences().forEach(r -> System.out.println(r.getIdentifier().orElse("?")+"\t"+r.getTitle().orElse("No title")));
		System.exit(0);
	}

}
