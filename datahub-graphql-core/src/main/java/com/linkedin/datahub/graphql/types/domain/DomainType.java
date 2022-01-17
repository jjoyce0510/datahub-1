package com.linkedin.datahub.graphql.types.domain;

import com.google.common.collect.ImmutableSet;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.Domain;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.entity.EntityResponse;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.Constants;
import graphql.execution.DataFetcherResult;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class DomainType implements com.linkedin.datahub.graphql.types.EntityType<Domain> {

  private final EntityClient _entityClient;

  public DomainType(final EntityClient entityClient)  {
    _entityClient = entityClient;
  }

  @Override
  public EntityType type() {
    return EntityType.DOMAIN;
  }

  @Override
  public Class<Domain> objectClass() {
    return Domain.class;
  }

  @Override
  public List<DataFetcherResult<Domain>> batchLoad(@Nonnull List<String> urns, @Nonnull QueryContext context) throws Exception {
    final Set<Urn> containerUrns = urns.stream()
        .map(this::getUrn)
        .collect(Collectors.toSet());

    try {
      final Map<Urn, EntityResponse> entities = _entityClient.batchGetV2(
          Constants.DOMAIN_ENTITY_NAME,
          containerUrns,
          ImmutableSet.of(
              Constants.DOMAIN_KEY_ASPECT_NAME,
              Constants.DOMAIN_PROPERTIES_ASPECT_NAME,
              Constants.OWNERSHIP_ASPECT_NAME,
              Constants.INSTITUTIONAL_MEMORY_ASPECT_NAME
          ),
          context.getAuthentication());

      final List<EntityResponse> gmsResults = new ArrayList<>();
      for (Urn urn : containerUrns) {
        gmsResults.add(entities.getOrDefault(urn, null));
      }
      return gmsResults.stream()
          .map(gmsResult ->
              gmsResult == null ? null : DataFetcherResult.<Domain>newResult()
                  .data(DomainMapper.map(gmsResult))
                  .build()
          )
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException("Failed to batch load Container", e);
    }
  }

  private Urn getUrn(final String urnStr) {
    try {
      return Urn.createFromString(urnStr);
    } catch (URISyntaxException e) {
      throw new RuntimeException(String.format("Failed to convert urn string %s into Urn", urnStr));
    }
  }
}