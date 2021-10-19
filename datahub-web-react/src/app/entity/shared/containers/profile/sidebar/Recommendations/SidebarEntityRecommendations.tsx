import React from 'react';
import styled from 'styled-components';
import { useListRecommendationsQuery } from '../../../../../../../graphql/recommendations.generated';
import {
    EntityType,
    ScenarioType,
    RecommendationModule as RecommendationModuleType,
} from '../../../../../../../types.generated';
import { RecommendationModule } from '../../../../../../recommendations/RecommendationModule';
import { RecommendationDisplayType } from '../../../../../../recommendations/renderer/RecommendationsRenderer';
import { SidebarHeader } from '../SidebarHeader';

const RecommendationsContainer = styled.div``;

export const SidebarEntityRecommendations = ({
    userUrn,
    entityUrn,
    entityType,
}: {
    userUrn: string;
    entityUrn: string;
    entityType: EntityType;
}) => {
    const { data } = useListRecommendationsQuery({
        variables: {
            input: {
                userUrn,
                requestContext: {
                    scenario: ScenarioType.EntityProfile,
                    entityRequestContext: {
                        urn: entityUrn,
                        type: entityType,
                    },
                },
                limit: 3,
            },
        },
        fetchPolicy: 'no-cache',
    });
    const recommendationModules = data?.listRecommendations?.modules;
    console.log(recommendationModules);
    return (
        <RecommendationsContainer>
            {recommendationModules &&
                recommendationModules.map((module) => (
                    <>
                        <SidebarHeader title={module.title} />
                        <RecommendationModule
                            displayType={RecommendationDisplayType.COMPACT}
                            module={module as RecommendationModuleType}
                            showTitle={false}
                        />
                    </>
                ))}
        </RecommendationsContainer>
    );
};
