package com.luxoft.eas026.module3;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Pipeline {

	private final static String BOOTSTRAP_SERVERS = ":9092,:9093,:9094";

	private final static String IN_TOPIC = "lowerCaseLetters";

	private final static String OUT_TOPIC = "upperCaseLetters";

	private final static String CLIENT_ID = "ext311";

	private final static String GROUP_ID = "ext311-group";

	public static void main(String[] args) {
		Properties prodProps = new Properties();
		prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
		prodProps.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
		prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		prodProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
		prodProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "ext311-tr");

		final Producer<String, String> producer = new KafkaProducer<>(prodProps);

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

		final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

		producer.initTransactions();
		try {
			consumer.subscribe(Collections.singleton(IN_TOPIC));
			while (true) {
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
				producer.beginTransaction();

				for (ConsumerRecord<String, String> data : records) {
					if (data.value() != null) {
						producer.send(
								new ProducerRecord<String, String>(OUT_TOPIC, data.key(), data.value().toUpperCase()));
					}
				}

				Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
				for (TopicPartition partition : records.partitions()) {
					List<ConsumerRecord<String, String>> recordsPerPartition = records.records(partition);
					long offset = recordsPerPartition.get(recordsPerPartition.size() - 1).offset();

					offsets.put(partition, new OffsetAndMetadata(offset + 1));
				}
				producer.sendOffsetsToTransaction(offsets, GROUP_ID);
				producer.commitTransaction();
			}
		} catch (Exception e) {
			producer.abortTransaction();
		} finally {
			producer.close();
			consumer.close();
		}
	}

}
