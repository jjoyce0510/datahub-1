package com.linkedin.datahub.graphql.types.tag;

import com.datahub.metadata.authorization.AuthorizationRequest;
import com.datahub.metadata.authorization.AuthorizationResult;
import com.datahub.metadata.authorization.Authorizer;
import com.linkedin.common.urn.CorpuserUrn;
import com.linkedin.common.urn.TagUrn;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.metadata.authorization.PoliciesConfig;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.AutoCompleteResults;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.FacetFilterInput;
import com.linkedin.datahub.graphql.generated.SearchResults;
import com.linkedin.datahub.graphql.generated.Tag;
import com.linkedin.datahub.graphql.generated.TagUpdate;
import com.linkedin.datahub.graphql.resolvers.ResolverUtils;
import com.linkedin.datahub.graphql.types.MutableType;
import com.linkedin.datahub.graphql.types.mappers.AutoCompleteResultsMapper;
import com.linkedin.datahub.graphql.types.mappers.UrnSearchResultsMapper;
import com.linkedin.datahub.graphql.types.tag.mappers.TagSnapshotMapper;
import com.linkedin.datahub.graphql.types.tag.mappers.TagUpdateSnapshotMapper;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.entity.Entity;
import com.linkedin.metadata.extractor.AspectExtractor;
import com.linkedin.metadata.query.AutoCompleteResult;
import com.linkedin.metadata.query.SearchResult;
import com.linkedin.metadata.snapshot.Snapshot;
import com.linkedin.metadata.snapshot.TagSnapshot;
import com.linkedin.r2.RemoteInvocationException;

import graphql.execution.DataFetcherResult;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TagType implements com.linkedin.datahub.graphql.types.SearchableEntityType<Tag>, MutableType<TagUpdate> {

    private static final Set<String> FACET_FIELDS = Collections.emptySet();

    private final EntityClient _tagClient;

    public TagType(final EntityClient tagClient) {
        _tagClient = tagClient;
    }

    @Override
    public Class<Tag> objectClass() {
        return Tag.class;
    }

    @Override
    public EntityType type() {
        return EntityType.TAG;
    }

    @Override
    public Class<TagUpdate> inputClass() {
        return TagUpdate.class;
    }

    @Override
    public List<DataFetcherResult<Tag>> batchLoad(final List<String> urns, final QueryContext context) {

        final List<TagUrn> tagUrns = urns.stream()
                .map(this::getTagUrn)
                .collect(Collectors.toList());

        try {
            final Map<Urn, Entity> tagMap = _tagClient.batchGet(tagUrns
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()),
                context.getActor());

            final List<Entity> gmsResults = new ArrayList<>();
            for (TagUrn urn : tagUrns) {
                gmsResults.add(tagMap.getOrDefault(urn, null));
            }
            return gmsResults.stream()
                    .map(gmsTag -> gmsTag == null ? null
                        : DataFetcherResult.<Tag>newResult()
                            .data(TagSnapshotMapper.map(gmsTag.getValue().getTagSnapshot()))
                            .localContext(AspectExtractor.extractAspects(gmsTag.getValue().getTagSnapshot()))
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch load Tags", e);
        }
    }

    @Override
    public SearchResults search(@Nonnull String query,
                                @Nullable List<FacetFilterInput> filters,
                                int start,
                                int count,
                                @Nonnull QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final SearchResult searchResult = _tagClient.search("tag", query, facetFilters, start, count, context.getActor());
        return UrnSearchResultsMapper.map(searchResult);
    }

    @Override
    public AutoCompleteResults autoComplete(@Nonnull String query,
                                            @Nullable String field,
                                            @Nullable List<FacetFilterInput> filters,
                                            int limit,
                                            @Nonnull QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final AutoCompleteResult result = _tagClient.autoComplete("tag", query, facetFilters, limit, context.getActor());
        return AutoCompleteResultsMapper.map(result);
    }


    @Override
    public Tag update(@Nonnull TagUpdate input, @Nonnull QueryContext context) throws Exception {
        if (isAuthorized(input, context)) {
            final CorpuserUrn actor = CorpuserUrn.createFromString(context.getActor());
            final TagSnapshot tagSnapshot = TagUpdateSnapshotMapper.map(input, actor);
            final Snapshot snapshot = Snapshot.create(tagSnapshot);
            try {
                Entity entity = new Entity();
                entity.setValue(snapshot);
                _tagClient.update(entity, context.getActor());
            } catch (RemoteInvocationException e) {
                throw new RuntimeException(String.format("Failed to write entity with urn %s", input.getUrn()), e);
            }

            return load(input.getUrn(), context).getData();
        }
        throw new AuthorizationException("Unauthorized to perform this action. Please contact your DataHub administrator.");
    }

    private TagUrn getTagUrn(final String urnStr) {
        try {
            return TagUrn.createFromString(urnStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Failed to retrieve tag with urn %s, invalid urn", urnStr));
        }
    }

    private boolean isAuthorized(@Nonnull TagUpdate update, @Nonnull QueryContext context) {
        // Decide whether the current principal should be allowed to update the Dataset.
        // First, check what is being updated.
        final Authorizer authorizer = context.getAuthorizer();
        final String principal = context.getActor();
        final String resourceUrn = update.getUrn();
        final String resourceType = PoliciesConfig.TAG_PRIVILEGES.getResourceType();
        final List<List<String>> requiredPrivileges = getRequiredPrivileges(update);
        final AuthorizationRequest.ResourceSpec resourceSpec = new AuthorizationRequest.ResourceSpec(resourceType, resourceUrn);

        for (List<String> privilegeGroup : requiredPrivileges) {
            if (isAuthorized(principal, privilegeGroup, resourceSpec, authorizer)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAuthorized(
        String principal,
        List<String> privilegeGroup,
        AuthorizationRequest.ResourceSpec resourceSpec,
        Authorizer authorizer) {
        // Each privilege in a group _must_ all be true to permit the operation.
        for (final String privilege : privilegeGroup) {
            // No "partial" operations. All privileges required for the update must be granted for it to succeed.
            final AuthorizationRequest request = new AuthorizationRequest(principal, privilege, Optional.of(resourceSpec));
            final AuthorizationResult result = authorizer.authorize(request);
            if (AuthorizationResult.Type.DENY.equals(result.getType())) {
                // Short circuit.
                return false;
            }
        }
        return true;
    }

    private List<List<String>> getRequiredPrivileges(final TagUpdate updateInput) {
        List<List<String>> orPrivileges = new ArrayList<>();

        List<String> allEntityPrivileges = new ArrayList<>();
        allEntityPrivileges.add(PoliciesConfig.EDIT_ENTITY_PRIVILEGE.getType());

        List<String> andPrivileges = new ArrayList<>();
        if (updateInput.getOwnership() != null) {
            andPrivileges.add(PoliciesConfig.EDIT_ENTITY_OWNERS_PRIVILEGE.getType());
        }
        if (updateInput.getDescription() != null || updateInput.getName() != null) {
            andPrivileges.add(PoliciesConfig.EDIT_ENTITY_PRIVILEGE.getType());
        }

        // If either set of privileges are all true, permit the operation.
        orPrivileges.add(allEntityPrivileges);
        orPrivileges.add(andPrivileges);
        return orPrivileges;
    }
}
