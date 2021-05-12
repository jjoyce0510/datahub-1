package com.linkedin.metadata.kafka;

import com.linkedin.data.schema.NamedDataSchema;
import com.linkedin.data.template.DataTemplate;
import com.linkedin.metadata.dao.exception.ModelConversionException;
import com.linkedin.metadata.dao.producer.BaseMetadataEventProducer;
import com.linkedin.metadata.dao.producer.KafkaMetadataEventProducer;
import com.linkedin.metadata.dao.producer.KafkaProducerCallback;
import com.linkedin.metadata.models.EntitySpec;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.mxe.TopicConvention;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.avro.generic.IndexedRecord;
import org.apache.kafka.clients.producer.Producer;


public class EntityEventProducer {

  private final Map<String, BaseMetadataEventProducer> _entityNameToProducer;

  public EntityEventProducer(@Nonnull final EntityRegistry registry,
      @Nonnull final Producer<String, ? extends IndexedRecord> producer,
      @Nonnull final TopicConvention topicConvention) {
    _entityNameToProducer = new HashMap<>();
    final List<EntitySpec> entitySpecs = registry.getEntitySpecs();
    for (final EntitySpec spec : entitySpecs) {
      // Create a new BaseMetadataEventProducer for Kafka
      _entityNameToProducer.put(spec.getName(),
          new KafkaMetadataEventProducer(getDataSchemaClassFromSchema(spec.getSnapshotSchema()),
              getDataSchemaClassFromSchema(spec.getAspectTyperefSchema()), producer, topicConvention, spec,
              new KafkaProducerCallback()));
    }
  }

  public BaseMetadataEventProducer getProducer(@Nonnull final String entityName) {
    return _entityNameToProducer.get(entityName);
  }

  private Class<? extends DataTemplate> getDataSchemaClassFromSchema(final NamedDataSchema schema) {
    Class<? extends DataTemplate> clazz;
    try {
      clazz = Class.forName(schema.getFullName()).asSubclass(DataTemplate.class);
    } catch (ClassNotFoundException e) {
      throw new ModelConversionException("Unable to find class " + schema.getFullName(), e);
    }
    return clazz;
  }
}
