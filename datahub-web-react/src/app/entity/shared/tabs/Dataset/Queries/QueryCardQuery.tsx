import React from 'react';
import styled from 'styled-components';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { ANTD_GRAY } from '../../../constants';

const Statement = styled.div<{ fullHeight?: boolean }>`
    background-color: ${ANTD_GRAY[2]};
    height: ${(props) => (props.fullHeight && '380px') || '240px'};
    margin: 0px 0px 4px 0px;
    border-radius: 8px;
    :hover {
        cursor: pointer;
    }
`;

const NestedSyntax = styled(SyntaxHighlighter)`
    background-color: transparent !important;
    border: none !important;
    height: 100%;
    margin: 0px;
`;

export type Props = {
    query: string;
    showDetails: boolean;
    onClickExpand?: (newQuery) => void;
};

export default function QueryCardQuery({ query, showDetails, onClickExpand }: Props) {
    return (
        <Statement fullHeight={!showDetails} onClick={onClickExpand}>
            <pre>
                <NestedSyntax showLineNumbers language="sql">
                    {query}
                </NestedSyntax>
            </pre>
        </Statement>
    );
}
