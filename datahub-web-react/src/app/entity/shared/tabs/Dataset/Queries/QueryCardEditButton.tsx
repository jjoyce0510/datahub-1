import React from 'react';
import styled from 'styled-components';
import { EditOutlined } from '@ant-design/icons';
import { Button } from 'antd';

const EditQueryActionButton = styled(Button)`
    && {
        margin: 0px;
        padding: 0px 4px 0px 4px;
    }
`;

export type Props = {
    onClickEdit?: () => void;
};

export default function QueryCardEditButton({ onClickEdit }: Props) {
    return (
        <EditQueryActionButton type="text" onClick={onClickEdit} data-testid="query-edit-button">
            <EditOutlined />
        </EditQueryActionButton>
    );
}
