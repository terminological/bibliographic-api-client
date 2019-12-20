package uk.co.terminological.bibliography.pmcidconv;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

//https://github.com/FasterXML/jackson-modules-java8
public class PMCIDResult implements Iterable<PMCIDRecord> {
	@JsonProperty("status") public Optional<String> status = Optional.empty();
	@JsonProperty("responseDate") public Optional<String> responseDate = Optional.empty();
	@JsonProperty("request") public Optional<String> request = Optional.empty();
	@JsonProperty("records") public List<PMCIDRecord> records = Collections.emptyList();
	@Override
	
	public Iterator<PMCIDRecord> iterator() {
		return records.iterator();
	}
}