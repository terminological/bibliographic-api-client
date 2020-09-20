package uk.co.terminological.bibliography.entrez;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static uk.co.terminological.bibliography.record.Builder.*;

import uk.co.terminological.bibliography.entrez.EntrezClient.Database;
import uk.co.terminological.bibliography.record.CitationLink;
import uk.co.terminological.bibliography.record.IdType;
import uk.co.terminological.bibliography.record.ImmutableCitationLink;
import uk.co.terminological.bibliography.record.ImmutableCitationReference;
import uk.co.terminological.bibliography.record.ImmutableRecordReference;
import uk.co.terminological.bibliography.record.Raw;
import uk.co.terminological.datatypes.FluentMap;
import uk.co.terminological.fluentxml.XmlElement;
import uk.co.terminological.fluentxml.XmlException;

public class EntrezLinks implements Raw<XmlElement> {

	private XmlElement raw;
	public EntrezLinks(XmlElement raw) throws XmlException {
		this.raw=raw; 
		convert();
	}
	public XmlElement getRaw() {return raw;}

	private List<EntrezLink> links;
	private static Map<String,IdType> lookup = FluentMap
			.with(Database.PUBMED.toString(), IdType.PMID)
			.and(Database.PMC.toString(), IdType.PMCID);

	public Stream<EntrezLink> stream() {
		return links.stream();
	}

	private void convert() throws XmlException {
		links = new ArrayList<EntrezLink>();
		
			for (XmlElement linkSet: raw.doXpath(".//LinkSet").getMany(XmlElement.class)) {

				Optional<String> dbFrom = linkSet.doXpath("./DbFrom").get(XmlElement.class).flatMap(el -> el.getTextContent());
				Optional<String> fromId = linkSet.doXpath("./IdList/Id").get(XmlElement.class).flatMap(el -> el.getTextContent());
				
				for (XmlElement linkSetDb: linkSet.doXpath("./LinkSetDb").getMany(XmlElement.class)) {
					
					Optional<String> dbTo = linkSetDb.doXpath("./DbTo").get(XmlElement.class).flatMap(el -> el.getTextContent());
					Optional<String> linkName = linkSetDb.doXpath("./LinkName").get(XmlElement.class).flatMap(el -> el.getTextContent());
					
					for (XmlElement link: linkSetDb.doXpath("./Link").getMany(XmlElement.class)) {
						
						Optional<String> toId = link.doXpath("./Id").get(XmlElement.class).flatMap(el -> el.getTextContent());
						Optional<Long> score = link.doXpath("./Score").get(XmlElement.class).flatMap(el -> el.getTextContent()).map(s -> Long.parseLong(s));
						
						if (dbFrom.isPresent() && fromId.isPresent() && toId.isPresent()) {
							links.add(new EntrezLink(dbFrom.get(),fromId.get(),linkName,dbTo,toId.get(), score));
						}
						
					}
					
				}
				
				for (XmlElement idUrlSet: linkSet.doXpath("./IdUrlList/IdUrlSet").getMany(XmlElement.class)) { 
					
					fromId = idUrlSet.doXpath("./Id").get(XmlElement.class).flatMap(el -> el.getTextContent());
					for (XmlElement objUrl: idUrlSet.doXpath("./ObjUrl").getMany(XmlElement.class)) {
						
						Optional<String> category = objUrl.doXpath("./Category").get(XmlElement.class).flatMap(el -> el.getTextContent());
						Optional<String> toUrl = objUrl.doXpath("./Url").get(XmlElement.class).flatMap(el -> el.getTextContent());
						
						if (dbFrom.isPresent() && fromId.isPresent() && toUrl.isPresent()) {
							links.add(new EntrezLink(dbFrom.get(),fromId.get(),category,toUrl.get()));
						}
					}
				}

				
			}
		
	}
	public List<EntrezLink> getLinks() {
		return links;
	}
	
	public Stream<CitationLink> getCitations(boolean invert) {
		return stream()
				.filter(l -> l.toId.isPresent())
				.map(l -> { 
					if (invert) {
						return new ImmutableCitationLink(
								new ImmutableCitationReference(
										new ImmutableRecordReference(lookup.get(l.toDbOrUrl),l.toId.get()),null,null),
								new ImmutableCitationReference(
										new ImmutableRecordReference(lookup.get(l.fromDb),l.fromId),null,null
										),
								Optional.empty()
							);
					} else {
						return new ImmutableCitationLink(
								new ImmutableCitationReference(
										new ImmutableRecordReference(lookup.get(l.fromDb),l.fromId),null,null
										),
								new ImmutableCitationReference(
										new ImmutableRecordReference(lookup.get(l.toDbOrUrl),l.toId.get()),null,null
										),
								Optional.empty()
							);
					}
				});
		
	}



	//public List<String> getSimilar

}