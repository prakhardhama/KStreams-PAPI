package processor;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Locale;


public class WordCountProcessor implements Processor<String, String> {

  private ProcessorContext context;
  private KeyValueStore<String, Long> kvStore;

  @Override
  @SuppressWarnings("unchecked")
  public void init(ProcessorContext context) {
      // keep the processor context locally because we need it in punctuate() and commit()
      this.context = context;

      // retrieve the key-value store named "Counts"
      kvStore = (KeyValueStore) context.getStateStore("Counts");

      // schedule a punctuate() method every 1000 milliseconds based on event-time
      this.context.schedule(1000, PunctuationType.STREAM_TIME, (timestamp) -> {
          KeyValueIterator<String, Long> iterator = this.kvStore.all();
          while (iterator.hasNext()) {
              KeyValue<String, Long> entry = iterator.next();
              context.forward(entry.key, entry.value.toString());
          }
          iterator.close();

          // commit the current processing progress
          context.commit();
      });
  }

  @Override
  public void process(String dummy, String line) {
      String[] words = line.toLowerCase(Locale.getDefault()).split(" ");

      for (String word : words) {
          Long oldValue = this.kvStore.get(word);

          if (oldValue == null) {
              this.kvStore.put(word, 1L);
          } else {
              this.kvStore.put(word, oldValue + 1);
          }
      }
  }

  @Override
  public void close() {
      // nothing to do
  }

}
