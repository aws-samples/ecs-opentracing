package com.mikelduke.vhs.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikelduke.opentracing.sparkjava.OpenTracingSparkFilters;

import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import spark.Request;
import spark.Response;
import spark.Spark;

public class VHSCatalogApplication {
	private static final String CLAZZ = VHSCatalogApplication.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLAZZ);

	private static ObjectMapper mapper = new ObjectMapper();
	private static Movies movies;

	public static void main(String[] args) {
		TracingConfiguration.configureGlobalTracer();
		loadMovies();

		OpenTracingSparkFilters sparkTracingFilters = new OpenTracingSparkFilters();
		Spark.before(sparkTracingFilters.before());
		Spark.before((req, res) -> {
			req.headers().forEach(key -> System.out.println(key + ": " + req.headers(key)));
		});
		Spark.afterAfter(sparkTracingFilters.afterAfter());
		Spark.exception(Exception.class, sparkTracingFilters.exception());

		Spark.get("movies", "application/json", (req, res) -> movies.getMovies(), VHSCatalogApplication::toJson);
		Spark.get("movies/:id", "application/json", VHSCatalogApplication::getMovie, VHSCatalogApplication::toJson);
		
		LOGGER.logp(Level.INFO, CLAZZ, "main", "Server started on port " + Spark.port());
	}

	private static Object getMovie(Request req, Response res) {
		res.type("application/json");

		Span parentSpan = req.attribute(OpenTracingSparkFilters.SERVER_SPAN);
		System.out.println("Parent Baggage Item: " + parentSpan.getBaggageItem("testBaggageItem".toLowerCase()));
		
		Span span = GlobalTracer.get()
				.buildSpan("getMovie")
				.asChildOf((Span) req.attribute(OpenTracingSparkFilters.SERVER_SPAN))
				.withTag("moveId", req.params("id"))
				.start();

		System.out.println("Baggage Item: " + span.getBaggageItem("testBaggageItem".toLowerCase()));

		Movie movie = movies.findOneById(Integer.parseInt(req.params("id")));
		
		span.log("movie: " + movie.getName());
		span.finish();

		return movie;
	}

	private static void loadMovies() {
		try (InputStream is = VHSCatalogApplication.class.getClassLoader().getResourceAsStream("movies.json")) {
			movies = mapper.readValue(is, Movies.class);
			System.out.println(movies);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static String toJson(Object o) throws JsonProcessingException {
		return mapper.writeValueAsString(o);
	}
}