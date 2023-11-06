import { Tooltip, Typography } from 'antd';
import { SelectValue } from 'antd/lib/select';
import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { ArrowRightOutlined } from '@ant-design/icons';
import { useGetAssertionRunsLazyQuery } from '../../../../../../graphql/assertion.generated';
import { AssertionResultType, AssertionRunStatus, EntityType } from '../../../../../../types.generated';
import { formatNumber } from '../../../../../shared/formatNumber';
import { getFixedLookbackWindow, getLocaleTimezone } from '../../../../../shared/time/timeUtils';
import { ANTD_GRAY } from '../../../constants';
import PrefixedSelect from '../Stats/historical/shared/PrefixedSelect';
import { LOOKBACK_WINDOWS } from '../Stats/lookbackWindows';
import { getResultColor, getResultErrorMessage, getResultIcon, getResultText } from './assertionUtils';
import { AssertionResultTimeline } from './AssertionResultTimeline';
import { DatasetAssertionResultDetails } from './DatasetAssertionResultDetails';
import { LinkWrapper } from '../../../../../shared/LinkWrapper';
import { useEntityRegistry } from '../../../../../useEntityRegistry';

const RESULT_CHART_WIDTH_PX = 800;

const LastEvaluatedAtLabel = styled.div`
    padding: 0;
    margin: 0;
    display: flex;
    align-items: center;
    color: ${ANTD_GRAY[7]};
`;

const AssertionResultIcon = styled.span`
    margin-right: 8px;
`;

const AssertionResultDetailsContainer = styled.div`
    margin-bottom: 4px;
`;

const AssertionResultErrorMessage = styled.div`
    max-width: 250px;
    margin-bottom: 4px;
`;

const AssertionResultInitializingMessage = styled.div`
    max-width: 250px;
    margin-bottom: 4px;
`;

const ContentContainer = styled.div`
    width: 100%;
    padding-left: 52px;
`;

const EvaluationsDetails = styled.div`
    width: ${RESULT_CHART_WIDTH_PX}px;
    padding-top: 12px;
`;

const EvaluationsHeader = styled.div`
    display: flex;
    justify-content: space-between;
`;

const EvaluationsSummary = styled.div`
    width: 300px;
    display: flex;
    align-items: center;
    justify-content: left;
`;

const EvaluationDateRange = styled(Typography.Text)`
    margin-right: 20px;
`;

const SucceededEvaluationsCount = styled.span`
    margin-right: 12px;
`;

const FailedEvaluationsCount = styled.span`
    margin-right: 12px;
`;

const ErrorEvaluationsContainer = styled.span`
    margin-right: 12px;
`;

const ErrorEvaluationsCount = styled(Typography.Text)`
    font-weight: 600;
    color: ${getResultColor(AssertionResultType.Error)};
`;

type Props = {
    urn: string;
    lastEvaluatedAtMillis?: number | undefined;
};

export const DatasetAssertionDetails = ({ urn, lastEvaluatedAtMillis }: Props) => {
    const [getAssertionRuns, { data }] = useGetAssertionRunsLazyQuery({ fetchPolicy: 'cache-first' });
    const entityRegistry = useEntityRegistry();

    /**
     * Set default window for fetching assertion history.
     */
    const [lookbackWindow, setLookbackWindow] = useState(LOOKBACK_WINDOWS.WEEK);
    useEffect(() => {
        getAssertionRuns({
            variables: { assertionUrn: urn, ...getFixedLookbackWindow(lookbackWindow.windowSize) },
        });
    }, [urn, lookbackWindow, getAssertionRuns]);

    /**
     * Invoked when user selects new lookback window (e.g. 1 year)
     */
    const onChangeLookbackWindow = (value: SelectValue) => {
        const newLookbackWindow = Object.values(LOOKBACK_WINDOWS).filter((window) => window.text === value?.valueOf());
        setLookbackWindow(newLookbackWindow[0]);
    };
    const selectedWindow = getFixedLookbackWindow(lookbackWindow.windowSize);
    const selectedWindowTimeRange = {
        startMs: selectedWindow.startTime,
        endMs: selectedWindow.endTime,
    };

    const completeAssertionRunEvents =
        data?.assertion?.runEvents?.runEvents.filter(
            (runEvent) => runEvent.status === AssertionRunStatus.Complete && runEvent.result,
        ) || [];

    /**
     * Last evaluated timestamp
     */
    const lastEvaluatedAt = lastEvaluatedAtMillis
        ? new Date(lastEvaluatedAtMillis)
        : completeAssertionRunEvents.length > 0 && new Date(completeAssertionRunEvents[0].timestampMillis);
    const localeTimezone = getLocaleTimezone();
    const lastEvaluatedTimeLocal =
        (lastEvaluatedAt &&
            `Last evaluated on ${lastEvaluatedAt.toLocaleDateString()} at ${lastEvaluatedAt.toLocaleTimeString()} (${localeTimezone})`) ||
        'No evaluations found';
    const lastEvaluatedTimeGMT = lastEvaluatedAt && lastEvaluatedAt.toUTCString();

    /**
     * Start and end date bounds for Chart
     */
    const startDate = new Date(selectedWindowTimeRange.startMs).toLocaleDateString('en-us', {
        month: 'short',
        day: 'numeric',
    });
    const endDate = new Date(selectedWindowTimeRange.endMs).toLocaleDateString('en-us', {
        month: 'short',
        day: 'numeric',
    });

    /**
     * Success / Failure summary
     */
    const succeededCount = data?.assertion?.runEvents?.succeeded;
    const failedCount = data?.assertion?.runEvents?.failed;
    const errorCount = data?.assertion?.runEvents?.errored;

    /**
     * Data for the chart of assertion results.
     */
    const assertionResultsChartData =
        completeAssertionRunEvents.map((runEvent) => {
            const { result } = runEvent;

            if (!result) throw new Error('Completed assertion run event does not have a result.');

            const resultTime = new Date(runEvent.timestampMillis);
            const localTime = resultTime.toLocaleString();
            const gmtTime = resultTime.toUTCString();
            const resultUrl = result.externalUrl;
            const isInitializing = result.type === AssertionResultType.Init;
            const errorMessage = getResultErrorMessage(result);
            const platformName = data?.assertion?.platform
                ? entityRegistry.getDisplayName(EntityType.DataPlatform, data?.assertion?.platform)
                : undefined;

            /**
             * Create a "result" to render in the timeline chart.
             */
            return {
                time: runEvent.timestampMillis,
                result: {
                    type: result.type,
                    resultUrl,
                    title: (
                        <>
                            <AssertionResultIcon>{getResultIcon(result.type)}</AssertionResultIcon>
                            <Typography.Text strong>{getResultText(result.type)}</Typography.Text>
                        </>
                    ),
                    content: (
                        <>
                            <AssertionResultDetailsContainer>
                                <DatasetAssertionResultDetails result={result} />
                            </AssertionResultDetailsContainer>
                            {isInitializing && (
                                <AssertionResultInitializingMessage>
                                    Collecting the information required to evaluate this assertion.
                                </AssertionResultInitializingMessage>
                            )}
                            {errorMessage && <AssertionResultErrorMessage>{errorMessage}</AssertionResultErrorMessage>}
                            <div>
                                <Tooltip title={`${gmtTime}`}>
                                    <Typography.Text type="secondary">{localTime}</Typography.Text>
                                </Tooltip>
                            </div>
                            {resultUrl && (
                                <LinkWrapper to={resultUrl} target="_blank">
                                    {platformName ? `View in ${platformName}` : 'View results'} <ArrowRightOutlined />
                                </LinkWrapper>
                            )}
                        </>
                    ),
                },
            };
        }) || [];

    return (
        <ContentContainer>
            <div>
                <Typography.Title level={5}>Evaluations</Typography.Title>
                <Tooltip placement="topLeft" title={lastEvaluatedTimeGMT}>
                    <LastEvaluatedAtLabel>{lastEvaluatedTimeLocal}</LastEvaluatedAtLabel>
                </Tooltip>
                <EvaluationsDetails>
                    <EvaluationsHeader>
                        <EvaluationsSummary>
                            <EvaluationDateRange strong>
                                {startDate} - {endDate}
                            </EvaluationDateRange>
                            <div>
                                <SucceededEvaluationsCount>
                                    <Typography.Text
                                        style={{ color: getResultColor(AssertionResultType.Success), fontWeight: 600 }}
                                    >
                                        {formatNumber(succeededCount)}
                                    </Typography.Text>{' '}
                                    passed
                                </SucceededEvaluationsCount>
                                <FailedEvaluationsCount>
                                    <Typography.Text
                                        style={{ color: getResultColor(AssertionResultType.Failure), fontWeight: 600 }}
                                    >
                                        {formatNumber(failedCount)}
                                    </Typography.Text>{' '}
                                    failed
                                </FailedEvaluationsCount>
                                {errorCount ? (
                                    <ErrorEvaluationsContainer>
                                        <ErrorEvaluationsCount>{formatNumber(errorCount)}</ErrorEvaluationsCount> error
                                        {errorCount > 1 ? 's' : ''}
                                    </ErrorEvaluationsContainer>
                                ) : null}
                            </div>
                        </EvaluationsSummary>
                        <PrefixedSelect
                            prefixText="Show "
                            values={Object.values(LOOKBACK_WINDOWS).map((window) => window.text)}
                            value={lookbackWindow.text}
                            setValue={onChangeLookbackWindow}
                        />
                    </EvaluationsHeader>
                    <AssertionResultTimeline
                        width={RESULT_CHART_WIDTH_PX}
                        data={assertionResultsChartData}
                        timeRange={selectedWindowTimeRange}
                    />
                </EvaluationsDetails>
            </div>
        </ContentContainer>
    );
};
