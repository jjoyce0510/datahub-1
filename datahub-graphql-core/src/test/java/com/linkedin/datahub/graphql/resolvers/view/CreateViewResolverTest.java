package com.linkedin.datahub.graphql.resolvers.view;

import com.datahub.authentication.Authentication;
import com.google.common.collect.ImmutableList;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.StringArray;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.CreateViewInput;
import com.linkedin.datahub.graphql.generated.DataHubViewDefinitionInput;
import com.linkedin.datahub.graphql.generated.DataHubViewFilterInput;
import com.linkedin.datahub.graphql.generated.DataHubViewType;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.FacetFilterInput;
import com.linkedin.datahub.graphql.generated.FilterOperator;
import com.linkedin.datahub.graphql.generated.LogicalOperator;
import com.linkedin.metadata.query.filter.Condition;
import com.linkedin.metadata.query.filter.ConjunctiveCriterion;
import com.linkedin.metadata.query.filter.ConjunctiveCriterionArray;
import com.linkedin.metadata.query.filter.Criterion;
import com.linkedin.metadata.query.filter.CriterionArray;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.service.ViewService;
import com.linkedin.view.DataHubViewDefinition;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.metadata.Constants;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletionException;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static com.linkedin.datahub.graphql.TestUtils.*;
import static org.testng.Assert.*;


public class CreateViewResolverTest {

  private static final CreateViewInput TEST_INPUT = new CreateViewInput(
      DataHubViewType.PERSONAL,
      "test-name",
      "test-description",
      new DataHubViewDefinitionInput(
          ImmutableList.of(EntityType.DATASET, EntityType.DASHBOARD),
          new DataHubViewFilterInput(
              LogicalOperator.AND,
              ImmutableList.of(
                  new FacetFilterInput("test1", null, ImmutableList.of("value1", "value2"), false, FilterOperator.EQUAL),
                  new FacetFilterInput("test2", null, ImmutableList.of("value1", "value2"), true, FilterOperator.IN)
              )
          )
      )
  );

  private static final Urn TEST_VIEW_URN = UrnUtils.getUrn("urn:li:dataHubView:test");

  @Test
  public void testGetSuccess() throws Exception {
    // Create resolver
    ViewService mockService = initMockService();
    CreateViewResolver resolver = new CreateViewResolver(mockService);

    // Execute resolver
    QueryContext mockContext = getMockAllowContext();
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);

    resolver.get(mockEnv).get();

    Mockito.verify(mockService, Mockito.times(1)).createView(
        Mockito.eq(com.linkedin.view.DataHubViewType.PERSONAL),
        Mockito.eq(TEST_INPUT.getName()),
        Mockito.eq(TEST_INPUT.getDescription()),
        Mockito.eq(
            new DataHubViewDefinition()
              .setEntityTypes(new StringArray(ImmutableList.of(Constants.DATASET_ENTITY_NAME, Constants.DASHBOARD_ENTITY_NAME)))
              .setFilter(new Filter()
                .setOr(new ConjunctiveCriterionArray(ImmutableList.of(
                    new ConjunctiveCriterion()
                      .setAnd(new CriterionArray(ImmutableList.of(
                          new Criterion()
                              .setCondition(Condition.EQUAL)
                              .setField("test1.keyword")
                              .setValue("value1") // Unfortunate --- For backwards compat.
                              .setValues(new StringArray(ImmutableList.of("value1", "value2")))
                              .setNegated(false),
                          new Criterion()
                              .setCondition(Condition.IN)
                              .setField("test2.keyword")
                              .setValue("value1") // Unfortunate --- For backwards compat.
                              .setValues(new StringArray(ImmutableList.of("value1", "value2")))
                              .setNegated(true)
                      )))
                ))
              )
        )), Mockito.any(Authentication.class), Mockito.anyLong());
  }

  @Test
  public void testGetUnauthorized() throws Exception {
    // Create resolver
    EntityClient mockClient = Mockito.mock(EntityClient.class);
    ViewService mockService = Mockito.mock(ViewService.class);
    CreateViewResolver resolver = new CreateViewResolver(mockService);

    // Execute resolver
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    QueryContext mockContext = getMockDenyContext();
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);

    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
    Mockito.verify(mockClient, Mockito.times(0)).ingestProposal(
        Mockito.any(),
        Mockito.any(Authentication.class));
  }

  @Test
  public void testGetViewServiceException() throws Exception {
    // Create resolver
    ViewService mockService = Mockito.mock(ViewService.class);
    Mockito.doThrow(RuntimeException.class).when(mockService).createView(
        Mockito.any(),
        Mockito.any(),
        Mockito.any(),
        Mockito.any(),
        Mockito.any(Authentication.class),
        Mockito.anyLong());

    CreateViewResolver resolver = new CreateViewResolver(mockService);

    // Execute resolver
    DataFetchingEnvironment mockEnv = Mockito.mock(DataFetchingEnvironment.class);
    QueryContext mockContext = getMockAllowContext();
    Mockito.when(mockEnv.getArgument(Mockito.eq("input"))).thenReturn(TEST_INPUT);
    Mockito.when(mockEnv.getContext()).thenReturn(mockContext);

    assertThrows(CompletionException.class, () -> resolver.get(mockEnv).join());
  }

  private ViewService initMockService() {
    ViewService service = Mockito.mock(ViewService.class);
    Mockito.when(service.createView(
        Mockito.eq(com.linkedin.view.DataHubViewType.PERSONAL),
        Mockito.eq(TEST_INPUT.getName()),
        Mockito.eq(TEST_INPUT.getDescription()),
        Mockito.any(),
        Mockito.any(Authentication.class),
        Mockito.anyLong()
    )).thenReturn(TEST_VIEW_URN);
    return service;
  }
}