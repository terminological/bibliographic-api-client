# Bibliographic API client
A Java library and R package exposing clients for various bibliographic APIs through a single format

This project is aimed to provide consistent Java clients for the web APIs of various bibliographic databases. It is an early stage and there is no documentation at present. The test directory has some end to end examples of use.

Design principles include: 
* Respecting rate limits and behave appropriately.
* Multi-thread support.
* Extensive caching of API results using EHCache, enabling citation network exploration without fear of repeatedly hitting same resources. 
* Fluent design of Java API to allow natural query construction.
* Exception free methods with use of Java 8 Optionals as query results, facilitating fault tolerant bulk operations using Java 8 Streams.
* Arbitrarily large querying with batching to multiple API calls.
* Verbose debugging mode. 
* TODO: Unified view of bibliographic reference from different APIs
* TODO: Integration of citeproc and citation output formats.

## Currently supported:
* Entrez APIs including Pubmed; eSearch, eFetch and eLinks
* CrossRef API
* Unpaywall API
* PMC IDConverter API
* Open access PDF retrieval and analysis including Cermine integration

## Example client usage

```java
BibliographicApis biblioApi = BibliographicApis.create(secretsPath);

Optional<Search> searchResult = biblioApi.getEntrez()
		.buildSearchQuery(search)
					.betweenDates(earliest, latest)
					.execute();
					
\\ Get a list of pubmed links for a List of pubmedIds
List<Link> tmp = biblioApi.getEntrez()
	.buildLinksQueryForIdsAndDatabase(listOfPmids, Database.PUBMED)
	.command(Command.NEIGHBOR_SCORE)
	.withLinkname("pubmed_pubmed")
	.searchLinked(searchWithin)
	.execute().stream()
	.flatMap(o -> o.stream()).collect(Collectors.toList());

\\ Get a single crossref result for a given doi
Optional<SingleResult> tmp = biblioApi.getCrossref().getByDoi(doi);

				
```
