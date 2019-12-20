package uk.co.terminological.bibliography;

import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.isomorphism.util.TokenBucket;
import org.isomorphism.util.TokenBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.LoggingFilter;

import uk.co.terminological.datatypes.StreamExceptions;
import uk.co.terminological.datatypes.StreamExceptions.FunctionWithException;



public abstract class CachingApiClient {

	protected CacheManager cacheManager;
	protected boolean debug = false;

	protected CachingApiClient(Optional<Path> optional, TokenBucket ratelimiter) {
		this.client = Client.create();
		if (optional.isPresent()) {
			StreamExceptions.tryRethrow(t -> Files.createDirectories(optional.get()));
			this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
					.with(CacheManagerBuilder.persistence(optional.get().toFile())) 
					.withCache(
							"forever",
							CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, BinaryData.class, 
									ResourcePoolsBuilder
									.heap(1000)
									.disk(40, MemoryUnit.MB, true))
							.withExpiry(ExpiryPolicyBuilder.noExpiration()))
					.withCache(
							"week",
							CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, BinaryData.class, 
									ResourcePoolsBuilder
									.heap(1000)
									.disk(40, MemoryUnit.MB, true))
							.withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofDays(7)))) 
					.build(); 
		} else {
			this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
					.withCache(
							"forever",
							CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, BinaryData.class, 
									ResourcePoolsBuilder
									.heap(10000)
									)
							.withExpiry(ExpiryPolicyBuilder.noExpiration())) 
					.withCache(
							"week",
							CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, BinaryData.class, 
									ResourcePoolsBuilder
									.heap(10000)
									).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofDays(7)))
							)
					.build();
		}
		cacheManager.init();
		Runtime.getRuntime().addShutdownHook( new Thread() {
			@Override
			public void run() {
				cacheManager.close();
			}
		});
		this.rateLimiter = ratelimiter;
	}

	protected Cache<String,BinaryData> permanentCache() {
		return cacheManager.getCache("forever", String.class, BinaryData.class);
	}

	protected Cache<String,BinaryData> tempCache() {
		return cacheManager.getCache("week", String.class, BinaryData.class);
	}

	public void debugMode() {
		this.debug = true;
		this.client.addFilter(new LoggingFilter(new java.util.logging.Logger("Jersey",null) {
			@Override public void info(String msg) { logger.info(msg); }
		}));
	}

	private static final Logger logger = LoggerFactory.getLogger(CachingApiClient.class);
	protected Client client;
	private TokenBucket rateLimiter;

	protected void rateLimit() {rateLimiter.consume();}

	protected static String encode(String string) {
		try {
			return URLEncoder.encode(string,java.nio.charset.StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	protected static String keyFromApiQuery(String url, MultivaluedMap<String, String> params) {
		return 
				"{\"url\":\""+url+"\",\"params\":{"+
				params.keySet().stream().sorted()
				.map(t ->  
				"\""+t+"\":["+params.get(t)
				.stream().sorted().map(s -> "\""+s.replace("\"", "\\\"")+"\"")
				.collect(Collectors.joining(","))+"]")
				.collect(Collectors.joining(","))+"}}";
	}

	protected void updateRateLimits(MultivaluedMap<String, String> headers) {
		try {
			Long rateLimitRequests = Long.parseLong(headers.get("X-Rate-Limit-Limit").get(0));
			Long rateLimitInterval = Long.parseLong(headers.get("X-Rate-Limit-Interval").get(0).replace("s", ""));
			rateLimiter = TokenBuckets.builder().withInitialTokens(rateLimiter.getNumTokens()).withCapacity(rateLimitRequests).withFixedIntervalRefillStrategy(rateLimitRequests,rateLimitInterval,TimeUnit.SECONDS).build();
		} catch (Exception e) {
			//Probably header wasn't set - just ignore
		}
	}

	protected abstract MultivaluedMap<String, String> defaultApiParams();


	protected <X> CallBuilder<X,Exception> buildCall(
			String url, Class<X> clazz) {return new CallBuilder<X,Exception>(url, defaultApiParams(), this);}

	protected static class CallBuilder<X,E extends Exception> {
		String url;
		MultivaluedMap<String,String> params;
		FunctionWithException<InputStream,X,E> operation;
		boolean temporary = true;
		CachingApiClient client;
		CallBuilder(String url, MultivaluedMap<String,String> params, CachingApiClient client) {
			this.url = url;
			this.params = params;
			this.client = client;
		}
		public CallBuilder<X,E> withParam(String key,String value) {
			params.add(key, value); 
			return this; 
		}
		public CallBuilder<X,E> withParams(MultivaluedMap<String,String> params) {
			this.params = params; 
			return this; 
		}
		public CallBuilder<X,E> withOperation(FunctionWithException<InputStream,X,E> operation) {
			this.operation = operation; 
			return this; 
		}
		public CallBuilder<X,E> cacheForever() {
			this.temporary=false; 
			return this; 
		}
		public Optional<X> get() {
			return client.call(url,params,temporary,"GET",operation);
		}
		public Optional<X> post() {
			return client.call(url,params,temporary,"POST",operation);
		}
	}

	private <X,E extends Exception> Optional<X> call(String url, MultivaluedMap<String,String> params, boolean temporary, String method, FunctionWithException<InputStream,X,E> operation) {
		if (operation == null) throw new NullPointerException("Operation must be defined");
		Cache<String,BinaryData> cache = temporary ? tempCache() : permanentCache();
		String key = keyFromApiQuery(url,params);
		if (cache.containsKey(key)) {
			logger.debug("Cache hit:" + key);
			try {
				X out = operation.apply(cache.get(key).inputStream());
				return Optional.of(out);
			} catch (Exception e) {
				if (debug) e.printStackTrace();
				logger.debug("Could not parse cached result:" + key);
				cache.remove(key);
			}
		}
		// Not cached or operation did not succeed
		rateLimit();
		logger.debug("Retrieving from API: "+key);
		try {
			WebResource wr = client.resource(url).queryParams(params);
			ClientResponse r = wr.method(method,ClientResponse.class);
			updateRateLimits(r.getHeaders());
			if (!r.getClientResponseStatus().getFamily().equals(Status.Family.SUCCESSFUL)) {
				logger.debug("API call failed with status {} : {}", r.getClientResponseStatus(), key);
				return Optional.empty();
			}
			BinaryData data = BinaryData.from(r.getEntityInputStream());
			X out = operation.apply(data.inputStream());
			cache.put(key, data);
			return Optional.of(out);
		} catch (Exception e) {
			if (debug) e.printStackTrace();
			logger.debug("Could not parse API result: "+key);
			return Optional.empty();
		}
	}

	protected Optional<InputStream> cachedStream(String key, boolean temporary, FunctionWithException<String,InputStream,Exception> supplier) {
		Cache<String,BinaryData> cache = temporary ? tempCache() : permanentCache();
		if (cache.containsKey(key)) {
			logger.debug("Cache hit:" + key);
			return Optional.of(cache.get(key).inputStream());
		} 
		try {
			BinaryData data = BinaryData.from(supplier.apply(key));
			cache.put(key, data);
			return Optional.of(data.inputStream());
		} catch (Exception e) {
			if (debug) e.printStackTrace();
			logger.debug("Could not open input stream: "+key);
			return Optional.empty();
		}

	}

	//TODO: A raw filesystem cache so that we can see the cache result - maybe alongside ehcache result.
	protected Optional<String> cachedString(String key, boolean temporary, FunctionWithException<String,String,Exception> supplier) {
		Cache<String,BinaryData> cache = temporary ? tempCache() : permanentCache();
		if (cache.containsKey(key)) {
			logger.debug("Cache hit:" + key);
			return Optional.of(cache.get(key).toString());
		}
		try {
			BinaryData data = BinaryData.from(supplier.apply(key));
			cache.put(key, data);
			return Optional.of(data.toString());
		} catch (Exception e) {
			if (debug) e.printStackTrace();
			logger.debug("Could get input string: "+key);
			return Optional.empty();
		}
	}

	//TODO: A raw filesystem cache so that we can see the cache result - maybe alongside ehcache result.
	protected <X extends Serializable> Optional<X> cachedObject(String key, boolean temporary, FunctionWithException<String,X,Exception> supplier) {
		Cache<String,BinaryData> cache = temporary ? tempCache() : permanentCache();
		if (cache.containsKey(key)) {
			logger.debug("Cache hit:" + key);
			try {
				return Optional.of(cache.get(key).toObject());
			} catch (BibliographicApiException e) {
				cache.remove(key);
			}
		}
		try {
			BinaryData data = BinaryData.from(supplier.apply(key));
			cache.put(key, data);
			return Optional.of(data.toObject());
		} catch (Exception e) {
			if (debug) e.printStackTrace();
			logger.debug("Could get input string: "+key);
			return Optional.empty();
		}

	}
}