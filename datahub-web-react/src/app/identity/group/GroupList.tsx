import React, { useState } from 'react';
import { Empty, List, message, Pagination } from 'antd';
import styled from 'styled-components';
import { CorpGroup } from '../../../types.generated';
import { Message } from '../../shared/Message';
import { useListGroupsQuery } from '../../../graphql/group.generated';
import GroupListItem from './GroupListItem';

const GroupContainer = styled.div``;

const GroupStyledList = styled(List)`
    &&& {
        width: 100%;
        border-color: ${(props) => props.theme.styles['border-color-base']};
        box-shadow: ${(props) => props.theme.styles['box-shadow']};
    }
`;

const GroupPaginationContainer = styled.div`
    display: flex;
    justify-content: center;
`;

const DEFAULT_PAGE_SIZE = 25;

export const GroupList = () => {
    const [page, setPage] = useState(1);
    const [removedUrns, setRemovedUrns] = useState<string[]>([]);

    // Policy list paging.
    const pageSize = DEFAULT_PAGE_SIZE;
    const start = (page - 1) * pageSize;

    const { loading, error, data, refetch } = useListGroupsQuery({
        variables: {
            input: {
                start,
                count: pageSize,
            },
        },
        fetchPolicy: 'no-cache',
    });

    const totalGroups = data?.listGroups?.total || 0;
    const groups = data?.listGroups?.groups || [];
    const filteredGroups = groups.filter((group) => !removedUrns.includes(group.urn));

    const onChangePage = (newPage: number) => {
        setPage(newPage);
    };

    const handleDelete = (urn: string) => {
        // Hack to deal with eventual consistency.
        const newRemovedUrns = [...removedUrns, urn];
        setRemovedUrns(newRemovedUrns);
        setTimeout(function () {
            refetch?.();
        }, 3000);
    };

    return (
        <>
            {!data && loading && <Message type="loading" content="Loading groups..." />}
            {error && message.error('Failed to load groups :(')}
            <GroupContainer>
                <GroupStyledList
                    bordered
                    locale={{
                        emptyText: <Empty description="No Groups!" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
                    }}
                    dataSource={filteredGroups}
                    renderItem={(item: any) => (
                        <GroupListItem onDelete={() => handleDelete(item.urn)} group={item as CorpGroup} />
                    )}
                />
                <GroupPaginationContainer>
                    <Pagination
                        style={{ margin: 40 }}
                        current={page}
                        pageSize={pageSize}
                        total={totalGroups}
                        showLessItems
                        onChange={onChangePage}
                        showSizeChanger={false}
                    />
                </GroupPaginationContainer>
            </GroupContainer>
        </>
    );
};
