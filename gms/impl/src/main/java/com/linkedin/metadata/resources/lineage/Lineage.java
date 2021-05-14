package com.linkedin.metadata.resources.lineage;

import com.linkedin.common.EntityRelationship;
import com.linkedin.common.EntityRelationshipArray;
import com.linkedin.common.EntityRelationships;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.dao.BaseQueryDAO;
import com.linkedin.metadata.graph.Neo4jGraphDAO;
import com.linkedin.metadata.query.CriterionArray;
import com.linkedin.metadata.query.Filter;
import com.linkedin.metadata.query.RelationshipDirection;
import com.linkedin.metadata.relationship.Consumes;
import com.linkedin.metadata.relationship.Contains;
import com.linkedin.metadata.relationship.DownstreamOf;
import com.linkedin.metadata.relationship.Produces;
import com.linkedin.metadata.resources.dashboard.Charts;
import com.linkedin.metadata.restli.RestliUtils;
import com.linkedin.parseq.Task;
import com.linkedin.restli.server.annotations.Optional;
import com.linkedin.restli.server.annotations.RestLiCollection;
import com.linkedin.restli.server.annotations.RestLiSimpleResource;
import com.linkedin.restli.server.annotations.RestMethod;
import com.linkedin.restli.server.resources.SimpleResourceTemplate;

import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import com.linkedin.restli.server.annotations.QueryParam;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.linkedin.metadata.dao.Neo4jUtil.createRelationshipFilter;
import static com.linkedin.metadata.dao.utils.QueryUtils.newFilter;



/**
 * Rest.li entry point: /lineage/{entityKey}?type={entityType}direction={direction}
 */
@RestLiSimpleResource(name = "lineage", namespace = "com.linkedin.lineage")
public final class Lineage extends SimpleResourceTemplate<EntityRelationships> {

    private static final String CHART_KEY = Charts.class.getAnnotation(RestLiCollection.class).keyName();
    private static final Filter EMPTY_FILTER = new Filter().setCriteria(new CriterionArray());
    private static final Integer MAX_DOWNSTREAM_CNT = 100;

    private static final List<String> LINEAGE_RELATIONSHIP_TYPES = Arrays.asList(
        "DownstreamOf", "Consumes", "Contains", "Produces");

    @Inject
    @Named("graphQueryDao")
    private Neo4jGraphDAO _graphQueryDao;

    public Lineage() {
        super();
    }

    static RelationshipDirection getOppositeDirection(RelationshipDirection direction) {
        if (direction.equals(RelationshipDirection.INCOMING)) {
            return RelationshipDirection.OUTGOING;
        }
        if (direction.equals(RelationshipDirection.OUTGOING)) {
            return RelationshipDirection.INCOMING;
        }
        return direction;
    }

    private List<Urn> getRelatedEntities(String rawUrn, List<String> relationshipTypes, RelationshipDirection direction) {
        return
            _graphQueryDao.findRelatedUrns("", newFilter("urn", rawUrn),
                "", EMPTY_FILTER,
                relationshipTypes, createRelationshipFilter(EMPTY_FILTER, direction),
                0, MAX_DOWNSTREAM_CNT)
                .stream().map(
                rawRelatedUrn -> {
                    try {
                        return Urn.createFromString(rawRelatedUrn);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            ).collect(Collectors.toList());
    }

    @Nonnull
    @RestMethod.Get
    public Task<EntityRelationships> get(
        @QueryParam("urn") @Nonnull String rawUrn,
            @QueryParam("direction") @Optional @Nullable String rawDirection
    ) throws URISyntaxException {
        RelationshipDirection direction = RelationshipDirection.valueOf(rawDirection);
        return RestliUtils.toTask(() -> {
            // TODO(gabe-lyons): support unions in Neo4jQueryDao queries & then we can remove the multiple DAO queries
            final List<Urn> downstreamOfEntities = getRelatedEntities(rawUrn, LINEAGE_RELATIONSHIP_TYPES, direction);

            final EntityRelationshipArray entityArray = new EntityRelationshipArray(
                    Stream.of(downstreamOfEntities).flatMap(Collection::stream)
                        .map(entity -> {
                            return new EntityRelationship()
                                    .setEntity(entity);
                        })
                        .collect(Collectors.toList())
                );

            return new EntityRelationships().setEntities(entityArray);
        });
    }
}
