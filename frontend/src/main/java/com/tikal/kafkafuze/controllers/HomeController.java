package com.tikal.kafkafuze.controllers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/metrics")
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern pattern =
        Pattern.compile("([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])");

    private ConcurrentHashMap<String, Long> localStore = new ConcurrentHashMap<>();

    public HomeController() {
        try {
            run();
        } catch (Exception e) {
            logger.error("error while setting up", e);
            e.printStackTrace();
        }
    }

    public static List<String> tryExtract(String value) {
        try {
            JsonNode root = mapper.readTree(value);

            String text = root.at("/text").asText("");

            if (text.equals("")) {
                return Arrays.asList();
            }
            
            List<String> allMatches = new ArrayList<String>();

            Matcher matcher = pattern.matcher(text);
    
            while (matcher.find()) {
                allMatches.add(matcher.group());
            }
    
            return allMatches;

        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList();
        }
    }

    @RequestMapping("")
    public HashMap<String, Long> getHomeRoutes() {

        HashMap<String, Long> homeRoutes = new HashMap<String, Long>();
        
        localStore.entrySet().stream()
            .sorted((a, b) -> (int) (b.getValue() - a.getValue()))
            .limit(10)
            .forEach(e -> {
                homeRoutes.put(e.getKey(), e.getValue());    
            });

        homeRoutes.put("message", Long.valueOf(localStore.size()));
        // homeRoutes.put("graphiql", "/graphiql");
        // homeRoutes.put("graphql", "/graphql");

        return homeRoutes;
    }
    
    public void run() throws Exception {

        logger.info("setting up stream-client");

        final String bootstrapServers = "localhost:29092";
        final Properties streamsConfiguration = new Properties();
        // Give the Streams application a unique name.  The name must be unique in the Kafka cluster
        // against which the application is run.
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-lambda-example");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "wordcount-lambda-example-client");
        // Where to find Kafka broker(s).
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Specify default (de)serializers for record keys and for record values.
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        // Records should be flushed every 10 seconds. This is less than the default
        // in order to keep this example interactive.
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);
        // For illustrative purposes we disable record caches
        streamsConfiguration.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        // Set up serializers and deserializers, which we will use for overriding the default serdes
        // specified above.
        final Serde<String> stringSerde = Serdes.String();
        final Serde<Long> longSerde = Serdes.Long();

        // define the processing topology of the Streams application.
        final StreamsBuilder builder = new StreamsBuilder();

        // Construct a `KStream` from the input topic "streams-plaintext-input", where message values
        // represent lines of text (for the sake of this example, we ignore whatever may be stored
        // in the message keys).
        //
        // Note: We could also just call `builder.stream("streams-plaintext-input")` if we wanted to leverage
        // the default serdes specified in the Streams configuration above, because these defaults
        // match what's in the actual topic.  However we explicitly set the deserializers in the
        // call to `stream()` below in order to show how that's done, too.
        final KStream<String, String> textLines = builder.stream("tweets");

        final KTable<Windowed<String>, Long> wordCounts = textLines
                // Split each text line, by whitespace, into words.  The text lines are the record
                // values, i.e. we can ignore whatever data is in the record keys and thus invoke
                // `flatMapValues()` instead of the more generic `flatMap()`.
                // .map((k, v) -> new KeyValue(k, v))
                // .mapValues(HomeController::tryExtract)
                // .flatMapValues(value -> Arrays.asList(pattern.split(value.toLowerCase())))
                .flatMapValues(HomeController::tryExtract)
                // Count the occurrences of each word (record key).
                //
                // This will change the stream type from `KStream<String, String>` to `KTable<String, Long>`
                // (word -> count).  In the `count` operation we must provide a name for the resulting KTable,
                // which will be used to name e.g. its associated state store and changelog topic.
                //
                // Note: no need to specify explicit serdes because the resulting key and value types match our default serde settings
                .groupBy((key, word) -> word)
                .windowedBy(TimeWindows.of(Duration.ofMinutes(100)))
                .re
                
                // .count();
                .count();
                    // Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("somestore")
                    //     .withKeySerde(Serdes.String())
                    //     .withValueSerde(Serdes.Long()));

        // Write the `KTable<String, Long>` to the output topic.
        wordCounts.toStream().foreach((key, value) -> {
            // logger.info("{}: {}", key.key(), value);
            this.localStore.put(key.key(), value);
        });
        
        // Now that we have finished the definition of the processing topology we can actually run
        // it via `start()`.  The Streams application as a whole can be launched just like any
        // normal Java application that has a `main()` method.
        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
        // Always (and unconditionally) clean local state prior to starting the processing topology.
        // We opt for this unconditional call here because this will make it easier for you to play around with the example
        // when resetting the application for doing a re-run (via the Application Reset Tool,
        // http://docs.confluent.io/current/streams/developer-guide.html#application-reset-tool).
        //
        // The drawback of cleaning up local state prior is that your app must rebuilt its local state from scratch, which
        // will take time and will require reading all the state-relevant data from the Kafka cluster over the network.
        // Thus in a production scenario you typically do not want to clean up always as we do here but rather only when it
        // is truly needed, i.e., only under certain conditions (e.g., the presence of a command line flag for your app).
        // See `ApplicationResetExample.java` for a production-like example.
        streams.cleanUp();
        streams.start();

        // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
