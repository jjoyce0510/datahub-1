import React from 'react';
import styled from 'styled-components';
import { Dropdown, Menu, Typography, Tooltip } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import { LogicalOperatorType } from './types';
import { getOperatorDisplayName } from './utils';
import { ANTD_GRAY } from '../../../../../entity/shared/constants';

const LogicalTypeTagContainer = styled.div`
    margin-right: 8px;
`;

const Operator = styled.div`
    padding: 8px;
    :hover {
        cursor: pointer;
    }
    border: 1px solid ${ANTD_GRAY[4.5]};
    border-radius: 4px;
    box-shadow: ${(props) => props.theme.styles['box-shadow']};
    display: flex;
    align-items: center;
    letter-spacing: 1px;
`;

const StyledDownOutlined = styled(DownOutlined)`
    margin-left: 2px;
    color: ${ANTD_GRAY[6]};
    &&& {
        font-size: 8px;
        padding-bottom: 2px;
    }
`;

const OptionDescription = styled(Typography.Paragraph)`
    && {
        margin: 0px;
        padding: 0px;
    }
`;

type Props = {
    operator: LogicalOperatorType;
    onSelectOperator: (operator) => void;
    predicateDisplayName?: string;
};

export const LogicalOperatorDropdown = ({ operator, onSelectOperator, predicateDisplayName = 'condition' }: Props) => {
    const operatorName = getOperatorDisplayName(operator);

    const menu = (
        <Menu onClick={(e) => onSelectOperator(e.key as LogicalOperatorType)}>
            <Menu.Item key={LogicalOperatorType.AND}>
                <Typography.Text strong>AND</Typography.Text>
                <OptionDescription type="secondary">All of the {predicateDisplayName}s must be true</OptionDescription>
            </Menu.Item>
            <Menu.Item key={LogicalOperatorType.OR}>
                <Typography.Text strong>OR</Typography.Text>
                <OptionDescription type="secondary">
                    At least one of the {predicateDisplayName}s must be true
                </OptionDescription>
            </Menu.Item>
            <Menu.Item key={LogicalOperatorType.NOT}>
                <Typography.Text strong>NOT</Typography.Text>
                <OptionDescription type="secondary">None of the {predicateDisplayName}s must be true</OptionDescription>
            </Menu.Item>
        </Menu>
    );

    return (
        <LogicalTypeTagContainer>
            <Dropdown overlay={menu}>
                <Tooltip title="Select a logical operator used when combining each statement in this block: And, Or, and Not.">
                    <Operator>
                        <b>{operatorName}</b>
                        <StyledDownOutlined />
                    </Operator>
                </Tooltip>
            </Dropdown>
        </LogicalTypeTagContainer>
    );
};
