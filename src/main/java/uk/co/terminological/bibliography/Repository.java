package uk.co.terminological.bibliography;

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
public class Repository {
	//TODO: Save and load repository on bibliographic API startup and shutdown
	//TODO: Use repository in API clients, to resolve references.
	//TODO: Methods to navigate repository
	//TODO: Is the repository a graph database? Is it a database?
	//TODO: Consider loading 217MB ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/ for id mapping
	//TODO: Consider JGraphT as in memory graph for determining expansion / shortest path etc
}
