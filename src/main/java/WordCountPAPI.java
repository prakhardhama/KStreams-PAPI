import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import processor.WordCountProcessor;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Demonstrates, using the low-level KStream Processor API, how to implement the WordCount program
 * that computes a simple word occurrence histogram from 3 different input texts.
 *
 * In this example, the input stream reads from a topic named "topic-input1", where the values of messages
 * represent lines of text; and the histogram output is written to topic "topic-output" where each record
 * is an updated count of a single word.
 *
 * Refer cmder directory to create topics, etc. before running streams application.
 */
public class WordCountPAPI {

    public static void main(String[] args) {
        Map<String, String> changelogConfig = new HashMap<>();
        // override min.insync.replicas
        changelogConfig.put("min.insyc.replicas", "1");

        StoreBuilder<KeyValueStore<String, Long>> countStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("Counts"),
                Serdes.String(),
                Serdes.Long())
                .withLoggingEnabled(changelogConfig); // enable change logging, with custom changelog settings

        Topology builder = new Topology();
        // add the source processor node that takes Kafka topic "source-topic" as input
        builder.addSource("Source", "topic-input1")
                // add the WordCountProcessor node which takes the source processor as its upstream processor
                .addProcessor("Processor", () -> new WordCountProcessor(), "Source")
                // add the count store associated with the WordCountProcessor processor
                .addStateStore(countStoreBuilder, "Processor")
                // add the sink processor node that takes Kafka topic "sink-topic" as output
                // and the WordCountProcessor node as its upstream processor
                .addSink("Sink", "topic-output", "Processor");

        System.out.println("Topology:\n" + builder.describe().toString());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-wordcountpapi");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
        // Note: To re-run the demo, you need to use the offset reset tool:
        // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        final KafkaStreams streams = new KafkaStreams(builder, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-wordcountpapi-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}