package uk.co.terminological.bibliography.scholar;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import uk.co.terminological.bibliography.CachingApiClient;
import uk.co.terminological.fluentxml.Xml;

//TODO: needs a bit of a rethink
@Deprecated
public class GoogleScholarClient extends CachingApiClient {

	static Logger logger = LoggerFactory.getLogger(GoogleScholarClient.class);
	
	static GoogleScholarClient instance = null;
	
	int maxRedirects = 10;
	Path cache = null;
	static String SCHOLAR_URL = "https://scholar.google.com/scholar"; //?hl=en&as_sdt=0%2C5&q=UK+CHIC+cholesterol&oq=
	
	private GoogleScholarClient(Optional<Path> cachePath) {
		super(cachePath, TokenBuckets.builder().withInitialTokens(10).withCapacity(10).withFixedIntervalRefillStrategy(10, 1, TimeUnit.SECONDS).build());
		ClientConfig config = new DefaultClientConfig();
	    config.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
	    client = Client.create(config);
	    client.setFollowRedirects(true);
	    client.addFilter(new ClientFilter() { 
            @Override 
            public ClientResponse handle(ClientRequest request) 
                            throws ClientHandlerException { 
                    request.getHeaders().add( 
                                    HttpHeaders.USER_AGENT, 
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.119 Safari/537.36");
                    request.getHeaders().add(
                    				HttpHeaders.ACCEPT,
                    				"text/html");
                    return getNext().handle(request); 
            } 
	    });
	    client.addFilter(new ClientFilter() {
	    	@Override
	        public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
	            ClientHandler ch = getNext();
	            int i=maxRedirects;
	            while (i-->0) {
		            ClientResponse resp = ch.handle(cr);
		            if (resp.getClientResponseStatus().getFamily() != Response.Status.Family.REDIRECTION) {
		                return resp;
		            } else {
		                // try location
		                String redirectTarget = resp.getHeaders().getFirst("Location");
		                logger.debug("redirecting to :"+redirectTarget);
		                cr.setURI(UriBuilder.fromUri(redirectTarget).build());
		                resp = ch.handle(cr);
		            }
	            }
	            throw new ClientHandlerException("Too many redirects");
	        }
	    });
	}
	
	public static GoogleScholarClient create() {
		return new GoogleScholarClient(Optional.empty());
	}
	
	public static GoogleScholarClient create(Path cachePath) {
		if (instance == null) instance = new GoogleScholarClient(Optional.ofNullable(cachePath));
		return instance;
	}
	
	public GoogleScholarClient maxRedirects(int redirects) {
		this.maxRedirects = redirects;
		return this;
	}
	
	public Optional<GoogleResult> findByString(String search) {
		
		return this
				.buildCall(SCHOLAR_URL, GoogleResult.class).withParam("q",search)
				.cacheForever()
				.withOperation(is -> {
					Xml resp = Xml.fromHtmlStream(is);
					return new GoogleResult(resp.content());
				}).get();
				
	}

	@Override
	protected MultivaluedMap<String, String> defaultApiParams() {
		return new MultivaluedMapImpl();
	}
	
	/*public List<GoogleResult> extractArticleRefs(String doi, InputStream is) {
		String key = "cermine_refs_"+doi;
		Optional<ArrayList<String>> references = this.cachedObject(key, false, k -> {
			ContentExtractor extractor = new ContentExtractor();
			extractor.setPDF(is);
			List<BibEntry> refs = extractor.getReferences();
			logger.info("Found {} references for {}", refs.size(), key);
			ArrayList<String> out = new ArrayList<>();
			refs.stream().filter(ref -> Arrays.asList(
						BibEntryType.ARTICLE,
						BibEntryType.INPROCEEDINGS,
						BibEntryType.PROCEEDINGS
					).contains(ref.getType()))
					.map(bib -> bib.getText())
					.forEach(s -> out.add(s));
			return out;
		});
		return references.orElse(new ArrayList<>());
		
	}*/
}
