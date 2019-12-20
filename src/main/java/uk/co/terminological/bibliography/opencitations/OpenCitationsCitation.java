package uk.co.terminological.bibliography.opencitations;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import uk.co.terminological.bibliography.ExtensibleJson;
import static uk.co.terminological.bibliography.record.Builder.*;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.CitationReference;
import uk.co.terminological.bibliography.record.IdType;

public class OpenCitationsCitation extends ExtensibleJson implements CitationLink {
	
	public OpenCitationsCitation(JsonNode node) { super(node); }
	
	public Optional<String> getCitingDoi() {return this.asString("citing").map(OpenCitationsCitation::value);}
	public Optional<String> getCitedDoi() {return this.asString("cited").map(OpenCitationsCitation::value);}
	public Optional<String> getCitingDate() {return this.asString("creation").map(OpenCitationsCitation::value);}
	public Optional<String> getCitationId() {return this.asString("oci").map(OpenCitationsCitation::value);}
	public Optional<String> getCitationIndex() {return this.asString("oci").map(OpenCitationsCitation::key);}
	
	private static String value(String response) {
		if (response.contains(" => "))
			return response.split(" => ")[1];
		else
			return response;
	}
	
	private static String key(String response) {
		if (response.contains(" => "))
			return response.split(" => ")[0];
		else
			return null;
	}

	@Override
	public CitationReference getSource() {
		return citationReference(
				recordReference(IdType.DOI, getCitingDoi().get()), null, null);
	}

	@Override
	public CitationReference getTarget() {
		return citationReference(
				recordReference(IdType.DOI, getCitedDoi().get()), null, null);
	}

	@Override
	public Optional<Integer> getIndex() {
		try {
			return getCitationIndex().map(Integer::parseInt);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
	
}
