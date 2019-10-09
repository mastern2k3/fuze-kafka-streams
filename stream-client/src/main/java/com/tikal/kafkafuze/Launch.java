package com.tikal.kafkafuze;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launch {

    private static final Logger logger = LoggerFactory.getLogger(Launch.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern pattern =
        Pattern.compile("([\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee])");

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

        } catch (IOException e) {
            e.printStackTrace();
            return Arrays.asList();
        }
    }

    public static void main(String[] args) throws IOException {

        logger.info("setting up stream-client");

        final String bootstrapServers = args.length > 0 ? args[0] : "localhost:29092";
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


        ArrayList<String> test = new ArrayList<String>();

        List<String> collect =
            test.stream().flatMap(str -> Arrays.stream(str.split(" "))).collect(Collectors.toList());

        // define the processing topology of the Streams application.
        final StreamsBuilder builder = new StreamsBuilder();
        final KStream<String, String> textLines = builder.stream("tweets");

        final KTable<Windowed<String>, Long> wordCounts = textLines
                // .flatMap((key, value) -> new KeyValue.<String, String[]>(key,Launch.tryExtract(value)))
                .flatMapValues(Launch::tryExtract)
                .groupBy((key, word) -> word)
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
                .count(
                    Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("somestore")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()));

        // Write the `KTable<String, Long>` to the output topic.
        // wordCounts.toStream().foreach((key, value) -> {
        //     logger.info("{}: {}", key.key(), value);
        // });
        
        wordCounts.toStream()
            .map((key, value) -> new KeyValue<>(key.key() + "@" + key.window().start() + "->" + key.window().end(), value))
            .to("somestore", Produced.with(stringSerde, longSerde));

        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);
        
        streams.cleanUp();
        streams.start();

        // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
