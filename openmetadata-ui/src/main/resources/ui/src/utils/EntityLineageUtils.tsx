/*
 *  Copyright 2022 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { CheckOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';
import { AxiosError } from 'axios';
import { CustomEdge } from 'components/Entity/EntityLineage/CustomEdge.component';
import CustomNodeV1 from 'components/Entity/EntityLineage/CustomNodeV1.component';
import {
  CustomEdgeData,
  CustomElement,
  CustomFlow,
  EdgeData,
  EdgeTypeEnum,
  EntityReferenceChild,
  LeafNodes,
  LineagePos,
  LoadingNodeState,
  ModifiedColumn,
  NodeIndexMap,
  SelectedEdge,
  SelectedNode,
} from 'components/Entity/EntityLineage/EntityLineage.interface';
import Loader from 'components/Loader/Loader';
import dagre from 'dagre';
import { t } from 'i18next';
import {
  cloneDeep,
  isEmpty,
  isEqual,
  isNil,
  isUndefined,
  lowerCase,
  uniqueId,
  uniqWith,
} from 'lodash';
import { LoadingState } from 'Models';
import React, { Fragment, MouseEvent as ReactMouseEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  Connection,
  Edge,
  isNode,
  MarkerType,
  Node,
  Position,
  ReactFlowInstance,
} from 'reactflow';
import { addLineage, deleteLineageEdge } from 'rest/miscAPI';
import { ReactComponent as DashboardIcon } from '../assets/svg/dashboard-grey.svg';
import { ReactComponent as MlModelIcon } from '../assets/svg/mlmodal.svg';
import { ReactComponent as PipelineIcon } from '../assets/svg/pipeline-grey.svg';
import { ReactComponent as TableIcon } from '../assets/svg/table-grey.svg';
import { ReactComponent as TopicIcon } from '../assets/svg/topic-grey.svg';
import {
  getContainerDetailPath,
  getDashboardDetailsPath,
  getDataModelDetailsPath,
  getMlModelPath,
  getPipelineDetailsPath,
  getTableTabPath,
  getTopicDetailsPath,
  INFO_COLOR,
} from '../constants/constants';
import {
  EXPANDED_NODE_HEIGHT,
  NODE_HEIGHT,
  NODE_WIDTH,
  ZOOM_VALUE,
} from '../constants/Lineage.constants';
import {
  EntityLineageDirection,
  EntityLineageNodeType,
  EntityType,
  FqnPart,
} from '../enums/entity.enum';
import { AddLineage } from '../generated/api/lineage/addLineage';
import { Column } from '../generated/entity/data/table';
import {
  ColumnLineage,
  Edge as EntityLineageEdge,
  EntityLineage,
  LineageDetails,
} from '../generated/type/entityLineage';
import { EntityReference } from '../generated/type/entityReference';
import {
  getPartialNameFromFQN,
  getPartialNameFromTableFQN,
  prepareLabel,
} from './CommonUtils';
import { getEntityName } from './EntityUtils';
import { getEntityLink } from './TableUtils';
import { showErrorToast } from './ToastUtils';

export const MAX_LINEAGE_LENGTH = 20;

export const getHeaderLabel = (
  name = '',
  fqn = '',
  type: string,
  isMainNode: boolean
) => {
  return (
    <Fragment>
      {isMainNode ? (
        <Typography.Text
          className="description-text text-left text-md font-medium w-68"
          data-testid="lineage-entity"
          ellipsis={{ tooltip: true }}>
          {name || prepareLabel(type, fqn, false)}
        </Typography.Text>
      ) : (
        <Typography.Title
          ellipsis
          className="m-b-0 text-base"
          level={5}
          title={name || prepareLabel(type, fqn, false)}>
          <Link className="" to={getEntityLink(type, fqn)}>
            <Button
              className="text-base font-semibold p-0"
              data-testid="link-button"
              type="link">
              {name || prepareLabel(type, fqn, false)}
            </Button>
          </Link>
        </Typography.Title>
      )}
    </Fragment>
  );
};

export const onLoad = (reactFlowInstance: ReactFlowInstance) => {
  reactFlowInstance.fitView();
  reactFlowInstance.zoomTo(ZOOM_VALUE);
};
/* eslint-disable-next-line */
export const onNodeMouseEnter = (_event: ReactMouseEvent, _node: Node) => {
  return;
};
/* eslint-disable-next-line */
export const onNodeMouseMove = (_event: ReactMouseEvent, _node: Node) => {
  return;
};
/* eslint-disable-next-line */
export const onNodeMouseLeave = (_event: ReactMouseEvent, _node: Node) => {
  return;
};
/* eslint-disable-next-line */
export const onNodeContextMenu = (event: ReactMouseEvent, _node: Node) => {
  event.preventDefault();
};

export const dragHandle = (event: ReactMouseEvent) => {
  event.stopPropagation();
};

const getNodeType = (
  entityLineage: EntityLineage,
  id: string
): EntityLineageNodeType => {
  const upStreamEdges = entityLineage.upstreamEdges || [];
  const downStreamEdges = entityLineage.downstreamEdges || [];

  const hasDownStreamToEntity = downStreamEdges.find(
    (down) => down.toEntity === id
  );
  const hasDownStreamFromEntity = downStreamEdges.find(
    (down) => down.fromEntity === id
  );
  const hasUpstreamFromEntity = upStreamEdges.find(
    (up) => up.fromEntity === id
  );
  const hasUpstreamToEntity = upStreamEdges.find((up) => up.toEntity === id);

  if (hasDownStreamToEntity && !hasDownStreamFromEntity) {
    return EntityLineageNodeType.OUTPUT;
  }
  if (hasUpstreamFromEntity && !hasUpstreamToEntity) {
    return EntityLineageNodeType.INPUT;
  }

  return EntityLineageNodeType.DEFAULT;
};

export const getColumnType = (edges: Edge[], id: string) => {
  const sourceEdge = edges.find((edge) => edge.sourceHandle === id);
  const targetEdge = edges.find((edge) => edge.targetHandle === id);

  if (sourceEdge?.sourceHandle === id && targetEdge?.targetHandle === id) {
    return EntityLineageNodeType.DEFAULT;
  }
  if (sourceEdge?.sourceHandle === id) {
    return EntityLineageNodeType.INPUT;
  }
  if (targetEdge?.targetHandle === id) {
    return EntityLineageNodeType.OUTPUT;
  }

  return EntityLineageNodeType.NOT_CONNECTED;
};

export const getLineageData = (
  entityLineage: EntityLineage,
  onSelect: (state: boolean, value: SelectedNode) => void,
  loadNodeHandler: (node: EntityReference, pos: LineagePos) => void,
  lineageLeafNodes: LeafNodes,
  isNodeLoading: LoadingNodeState,
  isEditMode: boolean,
  edgeType: string,
  onEdgeClick: (
    evt: React.MouseEvent<HTMLButtonElement>,
    data: CustomEdgeData
  ) => void,
  removeNodeHandler: (node: Node) => void,
  columns: { [key: string]: Column[] },
  addPipelineClick?: (
    evt: React.MouseEvent<HTMLButtonElement>,
    data: CustomEdgeData
  ) => void,
  handleColumnClick?: (value: string) => void,
  isExpanded?: boolean,
  onNodeExpand?: (isExpanded: boolean, node: EntityReference) => void
) => {
  const [x, y] = [0, 0];
  const nodes = [...(entityLineage['nodes'] || []), entityLineage['entity']];
  const edgesV1 = [
    ...(entityLineage.downstreamEdges || []),
    ...(entityLineage.upstreamEdges || []),
  ];
  const lineageEdgesV1: Edge[] = [];
  const mainNode = entityLineage['entity'];

  edgesV1.forEach((edge) => {
    const sourceType = nodes.find((n) => edge.fromEntity === n.id);
    const targetType = nodes.find((n) => edge.toEntity === n.id);

    if (isUndefined(sourceType) || isUndefined(targetType)) {
      return;
    }

    if (!isUndefined(edge.lineageDetails)) {
      edge.lineageDetails.columnsLineage?.forEach((e) => {
        const toColumn = e.toColumn || '';
        if (toColumn && e.fromColumns && e.fromColumns.length > 0) {
          e.fromColumns.forEach((fromColumn) => {
            lineageEdgesV1.push({
              id: `column-${fromColumn}-${toColumn}-edge-${edge.fromEntity}-${edge.toEntity}`,
              source: edge.fromEntity,
              target: edge.toEntity,
              targetHandle: toColumn,
              sourceHandle: fromColumn,
              type: edgeType,
              markerEnd: {
                type: MarkerType.ArrowClosed,
              },
              data: {
                id: `column-${fromColumn}-${toColumn}-edge-${edge.fromEntity}-${edge.toEntity}`,
                source: edge.fromEntity,
                target: edge.toEntity,
                targetHandle: toColumn,
                sourceHandle: fromColumn,
                isEditMode,
                onEdgeClick,
                isColumnLineage: true,
                isExpanded,
                columnFunctionValue: e.function,
                edge,
              },
            });
          });
        }
      });
    }

    lineageEdgesV1.push({
      id: `edge-${edge.fromEntity}-${edge.toEntity}`,
      source: `${edge.fromEntity}`,
      target: `${edge.toEntity}`,
      type: edgeType,
      animated: !isUndefined(edge.lineageDetails?.pipeline),
      style: { strokeWidth: '2px' },
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
      data: {
        id: `edge-${edge.fromEntity}-${edge.toEntity}`,
        label: getEntityName(edge.lineageDetails?.pipeline),
        pipeline: edge.lineageDetails?.pipeline,
        source: `${edge.fromEntity}`,
        target: `${edge.toEntity}`,
        sourceType: sourceType?.type,
        targetType: targetType?.type,
        isEditMode,
        onEdgeClick,
        addPipelineClick,
        isColumnLineage: false,
        isExpanded,
        edge,
      },
    });
  });

  const makeNode = (node: EntityReference) => {
    let type = node.type as EntityLineageNodeType;
    if (type !== EntityLineageNodeType.LOAD_MORE) {
      type = getNodeType(entityLineage, node.id);
    }
    const cols: { [key: string]: ModifiedColumn } = {};
    columns[node.id]?.forEach((col) => {
      cols[col.fullyQualifiedName || col.name] = {
        ...col,
        type:
          type === EntityLineageNodeType.LOAD_MORE
            ? type
            : isEditMode
            ? EntityLineageNodeType.DEFAULT
            : getColumnType(lineageEdgesV1, col.fullyQualifiedName || col.name),
      };
    });

    return {
      id: `${node.id}`,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      type:
        type === EntityLineageNodeType.LOAD_MORE || !isEditMode
          ? type
          : EntityLineageNodeType.DEFAULT,
      className: '',
      data: {
        entityType: node.type,
        lineageLeafNodes: lineageLeafNodes,
        removeNodeHandler,
        isEditMode,
        isExpanded,
        columns: cols,
        handleColumnClick,
        onNodeExpand,
        node,
        isNodeLoading,
        loadNodeHandler,
        onSelect,
      },
      position: {
        x: x,
        y: y,
      },
    };
  };

  const mainCols: { [key: string]: ModifiedColumn } = {};
  columns[mainNode.id]?.forEach((col) => {
    mainCols[col.fullyQualifiedName || col.name] = {
      ...col,
      type: isEditMode
        ? EntityLineageNodeType.DEFAULT
        : getColumnType(lineageEdgesV1, col.fullyQualifiedName || col.name),
    };
  });
  const mainNodeType = getNodeType(entityLineage, mainNode.id);
  const lineageData = [
    {
      id: `${mainNode.id}`,
      sourcePosition: 'right',
      targetPosition: 'left',
      type: mainNodeType,
      className: `core`,
      data: {
        isEditMode,
        removeNodeHandler,
        handleColumnClick,
        onNodeExpand,
        columns: mainCols,
        isExpanded,
        node: mainNode,
      },
      position: { x, y },
    },
  ];

  (entityLineage.nodes || []).forEach((n) => lineageData.push(makeNode(n)));

  return { node: lineageData, edge: lineageEdgesV1 };
};

export const getDeletedLineagePlaceholder = () => {
  return (
    <div className="m-t-md m-l-md global-border rounded-4 flex-center p-8 font-medium">
      <span>
        {t('message.lineage-data-is-not-available-for-deleted-entities')}
      </span>
    </div>
  );
};

export const getLayoutedElements = (
  elements: CustomElement,
  direction = EntityLineageDirection.LEFT_RIGHT
) => {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));

  const { node, edge } = elements;
  const isHorizontal = direction === EntityLineageDirection.LEFT_RIGHT;
  dagreGraph.setGraph({ rankdir: direction });

  const nodeIds = node.map((item) => item.id);

  node.forEach((el) => {
    const isExpanded = el.data.isExpanded;
    dagreGraph.setNode(el.id, {
      width: NODE_WIDTH,
      height: isExpanded ? EXPANDED_NODE_HEIGHT : NODE_HEIGHT,
    });
  });

  const edgesRequired: Edge[] = [];

  edge.forEach((el) => {
    if (
      nodeIds.indexOf(el.source) !== -1 &&
      nodeIds.indexOf(el.target) !== -1
    ) {
      edgesRequired.push(el);
      dagreGraph.setEdge(el.source, el.target);
    }
  });

  dagre.layout(dagreGraph);

  const uNode = node.map((el) => {
    const isExpanded = el.data.isExpanded;
    const nodeHight = isExpanded ? EXPANDED_NODE_HEIGHT : NODE_HEIGHT;
    const nodeWithPosition = dagreGraph.node(el.id);
    el.targetPosition = isHorizontal ? Position.Left : Position.Top;
    el.sourcePosition = isHorizontal ? Position.Right : Position.Bottom;
    el.position = {
      x: nodeWithPosition.x - NODE_WIDTH / 2,
      y: nodeWithPosition.y - nodeHight / 2,
    };

    return el;
  });

  return { node: uNode, edge: edgesRequired };
};

export const getModalBodyText = (selectedEdge: SelectedEdge) => {
  const { data, source, target } = selectedEdge;
  const { isColumnLineage } = data as CustomEdgeData;
  let sourceEntity = '';
  let targetEntity = '';
  const sourceFQN = isColumnLineage
    ? data?.sourceHandle
    : source.fullyQualifiedName;

  const targetFQN = isColumnLineage
    ? data?.targetHandle
    : target.fullyQualifiedName;

  const fqnPart = isColumnLineage ? FqnPart.Column : FqnPart.Table;

  if (source.type === EntityType.TABLE) {
    sourceEntity = getPartialNameFromTableFQN(sourceFQN || '', [fqnPart]);
  } else {
    sourceEntity = getPartialNameFromFQN(sourceFQN || '', ['database']);
  }

  if (target.type === EntityType.TABLE) {
    targetEntity = getPartialNameFromTableFQN(targetFQN || '', [fqnPart]);
  } else {
    targetEntity = getPartialNameFromFQN(targetFQN || '', ['database']);
  }

  return t('message.remove-edge-between-source-and-target', {
    sourceDisplayName: source.displayName ? source.displayName : sourceEntity,
    targetDisplayName: target.displayName ? target.displayName : targetEntity,
  });
};

export const getUniqueFlowElements = (elements: CustomFlow[]) => {
  const flag: { [x: string]: boolean } = {};
  const uniqueElements: CustomFlow[] = [];

  elements.forEach((elem) => {
    if (!flag[elem.id]) {
      flag[elem.id] = true;
      uniqueElements.push(elem);
    }
  });

  return uniqueElements;
};

export const getSelectedEdgeArr = (
  edgeArr: EntityLineageEdge[],
  edgeData: EdgeData
) => {
  return edgeArr.filter(
    (edge) =>
      !edgeArr.find(
        () =>
          edgeData.fromId === edge.fromEntity && edgeData.toId === edge.toEntity
      )
  );
};

/**
 * Finds the upstream/downstream edge based on selected edge
 * @param edgeArr edge[]
 * @param data selected edge
 * @returns edge
 */

export const findUpstreamDownStreamEdge = (
  edgeArr: EntityLineageEdge[] | undefined,
  data: SelectedEdge
) => {
  return edgeArr?.find(
    (edge) =>
      edge.fromEntity === data.source.id && edge.toEntity === data.target.id
  );
};

/**
 * Get upstream/downstream column lineage array
 * @param lineageDetails LineageDetails
 * @param data SelectedEdge
 * @returns Updated LineageDetails
 */

export const getUpStreamDownStreamColumnLineageArr = (
  lineageDetails: LineageDetails,
  data: SelectedEdge
) => {
  const columnsLineage = lineageDetails.columnsLineage?.reduce((col, curr) => {
    if (curr.toColumn === data.data?.targetHandle) {
      const newCol = {
        ...curr,
        fromColumns:
          curr.fromColumns?.filter(
            (column) => column !== data.data?.sourceHandle
          ) || [],
      };
      if (newCol.fromColumns?.length) {
        return [...col, newCol];
      } else {
        return col;
      }
    }

    return [...col, curr];
  }, [] as ColumnLineage[]);

  return {
    sqlQuery: lineageDetails.sqlQuery || '',
    columnsLineage: columnsLineage,
  };
};

/**
 * Get updated EntityLineageEdge Array based on selected data
 * @param edge EntityLineageEdge[]
 * @param data SelectedEdge
 * @param lineageDetails updated LineageDetails
 * @returns updated EntityLineageEdge[]
 */
export const getUpdatedUpstreamDownStreamEdgeArr = (
  edge: EntityLineageEdge[],
  data: SelectedEdge,
  lineageDetails: LineageDetails
) => {
  return edge.map((down) => {
    if (
      down.fromEntity === data.source.id &&
      down.toEntity === data.target.id
    ) {
      return {
        ...down,
        lineageDetails: lineageDetails,
      };
    }

    return down;
  });
};

/**
 * Get array of the removed node
 * @param nodes All the node
 * @param edge selected edge
 * @param entity main entity
 * @param selectedEntity selected entity
 * @returns details of removed node
 */
export const getRemovedNodeData = (
  nodes: EntityReference[],
  edge: Edge,
  entity: EntityReference,
  selectedEntity: EntityReference
) => {
  let targetNode = nodes.find((node) => edge.target?.includes(node.id));
  let sourceNode = nodes.find((node) => edge.source?.includes(node.id));
  const selectedNode = isEmpty(selectedEntity) ? entity : selectedEntity;

  if (isUndefined(targetNode)) {
    targetNode = selectedNode;
  }
  if (isUndefined(sourceNode)) {
    sourceNode = selectedNode;
  }

  return {
    id: edge.id,
    source: sourceNode,
    target: targetNode,
  };
};

/**
 * Get source/target edge based on query string
 * @param edge upstream/downstream edge array
 * @param queryStr source/target string
 * @param id main entity id
 * @returns source/target edge
 */
const getSourceTargetNode = (
  edge: EntityLineageEdge[],
  queryStr: string | null,
  id: string
) => {
  return edge.find(
    (d) =>
      (queryStr?.includes(d.fromEntity) || queryStr?.includes(d.toEntity)) &&
      queryStr !== id
  );
};

export const getEdgeType = (
  updatedLineageData: EntityLineage,
  params: Edge | Connection
) => {
  const { entity } = updatedLineageData;
  const { target, source } = params;
  const sourceDownstreamNode = getSourceTargetNode(
    updatedLineageData.downstreamEdges || [],
    source,
    entity.id
  );

  const sourceUpStreamNode = getSourceTargetNode(
    updatedLineageData.upstreamEdges || [],
    source,
    entity.id
  );

  const targetDownStreamNode = getSourceTargetNode(
    updatedLineageData.downstreamEdges || [],
    target,
    entity.id
  );

  const targetUpStreamNode = getSourceTargetNode(
    updatedLineageData.upstreamEdges || [],
    target,
    entity.id
  );

  const isUpstream =
    (!isNil(sourceUpStreamNode) && !isNil(targetDownStreamNode)) ||
    !isNil(sourceUpStreamNode) ||
    !isNil(targetUpStreamNode) ||
    target?.includes(entity.id);

  const isDownstream =
    (!isNil(sourceDownstreamNode) && !isNil(targetUpStreamNode)) ||
    !isNil(sourceDownstreamNode) ||
    !isNil(targetDownStreamNode) ||
    source?.includes(entity.id);

  if (isUpstream) {
    return EdgeTypeEnum.UP_STREAM;
  } else if (isDownstream) {
    return EdgeTypeEnum.DOWN_STREAM;
  }

  return EdgeTypeEnum.NO_STREAM;
};

/**
 * Get updated Edge with lineageDetails
 * @param edges Array of Edge
 * @param params new connected edge
 * @param lineageDetails updated lineage details
 * @returns updated edge array
 */
export const getUpdatedEdge = (
  edges: EntityLineageEdge[],
  params: Edge | Connection,
  lineageDetails: LineageDetails | undefined
) => {
  const updatedEdge: EntityLineageEdge[] = [];
  const { target, source } = params;
  edges.forEach((edge) => {
    if (edge.fromEntity === source && edge.toEntity === target) {
      updatedEdge.push({
        ...edge,
        lineageDetails: lineageDetails,
      });
    } else {
      updatedEdge.push(edge);
    }
  });

  return updatedEdge;
};

// create new edge
export const createNewEdge = (
  params: Edge | Connection,
  isEditMode: boolean,
  sourceNodeType: string,
  targetNodeType: string,
  isColumnLineage: boolean,
  onEdgeClick: (
    evt: React.MouseEvent<HTMLButtonElement>,
    data: CustomEdgeData
  ) => void,
  addPipelineClick: (
    evt: React.MouseEvent<HTMLButtonElement>,
    data: CustomEdgeData
  ) => void
) => {
  const { target, source, sourceHandle, targetHandle } = params;
  let data: Edge = {
    id: `edge-${source}-${target}`,
    source: `${source}`,
    target: `${target}`,
    type: isEditMode ? 'buttonedge' : 'default',
    style: { strokeWidth: '2px' },
    markerEnd: {
      type: MarkerType.ArrowClosed,
    },
    data: {
      id: `edge-${source}-${target}`,
      source: source,
      target: target,
      sourceType: sourceNodeType,
      targetType: targetNodeType,
      isColumnLineage: isColumnLineage,
      onEdgeClick,
      isEditMode,
      addPipelineClick,
    },
  };

  if (isColumnLineage) {
    data = {
      ...data,
      id: `column-${sourceHandle}-${targetHandle}-edge-${source}-${target}`,
      sourceHandle: sourceHandle,
      targetHandle: targetHandle,
      style: undefined,
      data: {
        ...data.data,
        id: `column-${sourceHandle}-${targetHandle}-edge-${source}-${target}`,
        sourceHandle: sourceHandle,
        targetHandle: targetHandle,
        addPipelineClick: undefined,
      },
    };
  }

  return data;
};

export const getUpdatedEdgeWithPipeline = (
  edges: EntityLineage['downstreamEdges'],
  updatedLineageDetails: LineageDetails,
  selectedEdge: CustomEdgeData,
  pipelineDetail: EntityReference | undefined
) => {
  if (isUndefined(edges)) {
    return [];
  }

  const { source, target } = selectedEdge;

  return edges.map((edge) => {
    if (edge.fromEntity === source && edge.toEntity === target) {
      return {
        ...edge,
        lineageDetails: {
          ...updatedLineageDetails,
          pipeline: !isUndefined(updatedLineageDetails.pipeline)
            ? {
                displayName: pipelineDetail?.displayName,
                name: pipelineDetail?.name,
                ...updatedLineageDetails.pipeline,
              }
            : undefined,
        },
      };
    }

    return edge;
  });
};

export const getNewLineageConnectionDetails = (
  selectedEdgeValue: EntityLineageEdge | undefined,
  selectedPipelineId: string | undefined,
  customEdgeData: CustomEdgeData
) => {
  const { source, sourceType, target, targetType } = customEdgeData;
  const updatedLineageDetails: LineageDetails = {
    ...selectedEdgeValue?.lineageDetails,
    sqlQuery: selectedEdgeValue?.lineageDetails?.sqlQuery || '',
    columnsLineage: selectedEdgeValue?.lineageDetails?.columnsLineage || [],
    pipeline: isUndefined(selectedPipelineId)
      ? undefined
      : {
          id: selectedPipelineId,
          type: EntityType.PIPELINE,
        },
  };

  const newEdge: AddLineage = {
    edge: {
      fromEntity: {
        id: source,
        type: sourceType,
      },
      toEntity: {
        id: target,
        type: targetType,
      },
      lineageDetails: updatedLineageDetails,
    },
  };

  return {
    updatedLineageDetails,
    newEdge,
  };
};

export const getLoadingStatusValue = (
  defaultState: string | JSX.Element,
  loading: boolean,
  status: LoadingState
) => {
  if (loading) {
    return <Loader size="small" type="white" />;
  } else if (status === 'success') {
    return <CheckOutlined className="text-white" />;
  } else {
    return defaultState;
  }
};

const getTracedNode = (
  node: Node,
  nodes: Node[],
  edges: Edge[],
  isIncomer: boolean
) => {
  if (!isNode(node)) {
    return [];
  }

  const tracedEdgeIds = edges
    .filter((e) => {
      const id = isIncomer ? e.target : e.source;

      return id === node.id;
    })
    .map((e) => (isIncomer ? e.source : e.target));

  return nodes.filter((n) =>
    tracedEdgeIds
      .map((id) => {
        const matches = /([\w-^]+)__([\w-]+)/.exec(id);
        if (matches === null) {
          return id;
        }

        return matches[1];
      })
      .includes(n.id)
  );
};

export const getAllTracedNodes = (
  node: Node,
  nodes: Node[],
  edges: Edge[],
  prevTraced = [] as Node[],
  isIncomer: boolean
) => {
  const tracedNodes = getTracedNode(node, nodes, edges, isIncomer);

  return tracedNodes.reduce((memo, tracedNode) => {
    memo.push(tracedNode);

    if (prevTraced.findIndex((n) => n.id === tracedNode.id) === -1) {
      prevTraced.push(tracedNode);

      getAllTracedNodes(
        tracedNode,
        nodes,
        edges,
        prevTraced,
        isIncomer
      ).forEach((foundNode) => {
        memo.push(foundNode);

        if (prevTraced.findIndex((n) => n.id === foundNode.id) === -1) {
          prevTraced.push(foundNode);
        }
      });
    }

    return memo;
  }, [] as Node[]);
};

export const getClassifiedEdge = (edges: Edge[]) => {
  return edges.reduce(
    (acc, edge) => {
      if (isUndefined(edge.sourceHandle) && isUndefined(edge.targetHandle)) {
        acc.normalEdge.push(edge);
      } else {
        acc.columnEdge.push(edge);
      }

      return acc;
    },
    {
      normalEdge: [] as Edge[],
      columnEdge: [] as Edge[],
    }
  );
};

export const isTracedEdge = (
  selectedNode: Node,
  edge: Edge,
  incomerIds: string[],
  outgoerIds: string[]
) => {
  const incomerEdges =
    incomerIds.includes(edge.source) &&
    (incomerIds.includes(edge.target) || selectedNode.id === edge.target);
  const outgoersEdges =
    outgoerIds.includes(edge.target) &&
    (outgoerIds.includes(edge.source) || selectedNode.id === edge.source);

  return (
    (incomerEdges || outgoersEdges) &&
    isUndefined(edge.sourceHandle) &&
    isUndefined(edge.targetHandle)
  );
};

const getTracedEdge = (
  selectedColumn: string,
  edges: Edge[],
  isIncomer: boolean
) => {
  if (isEmpty(selectedColumn)) {
    return [];
  }

  const tracedEdgeIds = edges
    .filter((e) => {
      const id = isIncomer ? e.targetHandle : e.sourceHandle;

      return id === selectedColumn;
    })
    .map((e) => (isIncomer ? `${e.sourceHandle}` : `${e.targetHandle}`));

  return tracedEdgeIds;
};

export const getAllTracedEdges = (
  selectedColumn: string,
  edges: Edge[],
  prevTraced = [] as string[],
  isIncomer: boolean
) => {
  const tracedNodes = getTracedEdge(selectedColumn, edges, isIncomer);

  return tracedNodes.reduce((memo, tracedNode) => {
    memo.push(tracedNode);

    if (prevTraced.findIndex((n) => n === tracedNode) === -1) {
      prevTraced.push(tracedNode);

      getAllTracedEdges(tracedNode, edges, prevTraced, isIncomer).forEach(
        (foundNode) => {
          memo.push(foundNode);

          if (prevTraced.findIndex((n) => n === foundNode) === -1) {
            prevTraced.push(foundNode);
          }
        }
      );
    }

    return memo;
  }, [] as string[]);
};

export const getAllTracedColumnEdge = (column: string, columnEdge: Edge[]) => {
  const incomingColumnEdges = getAllTracedEdges(column, columnEdge, [], true);
  const outGoingColumnEdges = getAllTracedEdges(column, columnEdge, [], false);

  return {
    incomingColumnEdges,
    outGoingColumnEdges,
    connectedColumnEdges: [
      column,
      ...incomingColumnEdges,
      ...outGoingColumnEdges,
    ],
  };
};

export const isColumnLineageTraced = (
  column: string,
  edge: Edge,
  incomingColumnEdges: string[],
  outGoingColumnEdges: string[]
) => {
  const incomerEdges =
    incomingColumnEdges.includes(`${edge.sourceHandle}`) &&
    (incomingColumnEdges.includes(`${edge.targetHandle}`) ||
      column === edge.targetHandle);
  const outgoersEdges =
    outGoingColumnEdges.includes(`${edge.targetHandle}`) &&
    (outGoingColumnEdges.includes(`${edge.sourceHandle}`) ||
      column === edge.sourceHandle);

  return incomerEdges || outgoersEdges;
};

export const getEdgeStyle = (value: boolean) => {
  return {
    opacity: value ? 1 : 0.25,
    strokeWidth: value ? 2 : 1,
    stroke: value ? INFO_COLOR : undefined,
  };
};

export const getChildMap = (obj: EntityLineage) => {
  const nodeSet = new Set<string>();
  nodeSet.add(obj.entity.id);
  const newData = cloneDeep(obj);
  newData.downstreamEdges = removeDuplicates(newData.downstreamEdges || []);
  newData.upstreamEdges = removeDuplicates(newData.upstreamEdges || []);

  const childMap: EntityReferenceChild[] = getLineageChildParents(
    newData,
    nodeSet,
    obj.entity.id,
    false
  );

  const parentsMap: EntityReferenceChild[] = getLineageChildParents(
    newData,
    nodeSet,
    obj.entity.id,
    true
  );

  const map: EntityReferenceChild = {
    ...obj.entity,
    children: childMap,
    parents: parentsMap,
  };

  return map;
};

export const getPaginatedChildMap = (
  obj: EntityLineage,
  map: EntityReferenceChild | undefined,
  pagination_data: Record<string, NodeIndexMap>,
  maxLineageLength: number
) => {
  const nodes = [];
  const edges: EntityLineageEdge[] = [];
  nodes.push(obj.entity);
  if (map) {
    flattenObj(
      obj,
      map,
      true,
      obj.entity.id,
      nodes,
      edges,
      pagination_data,
      maxLineageLength
    );
    flattenObj(
      obj,
      map,
      false,
      obj.entity.id,
      nodes,
      edges,
      pagination_data,
      maxLineageLength
    );
  }

  return { nodes, edges };
};

export const flattenObj = (
  entityObj: EntityLineage,
  childMapObj: EntityReferenceChild,
  downwards: boolean,
  id: string,
  nodes: EntityReference[],
  edges: EntityLineageEdge[],
  pagination_data: Record<string, NodeIndexMap>,
  maxLineageLength = 50
) => {
  const children = downwards ? childMapObj.children : childMapObj.parents;
  if (!children) {
    return;
  }
  const startIndex =
    pagination_data[id]?.[downwards ? 'downstream' : 'upstream'][0] ?? 0;
  const hasMoreThanLimit = children.length > startIndex + maxLineageLength;
  const endIndex = startIndex + maxLineageLength;

  children.slice(0, endIndex).forEach((item) => {
    if (item) {
      flattenObj(
        entityObj,
        item,
        downwards,
        item.id,
        nodes,
        edges,
        pagination_data,
        maxLineageLength
      );
      nodes.push(item);
    }
  });

  if (hasMoreThanLimit) {
    const newNodeId = `loadmore_${uniqueId('node_')}_${id}_${startIndex}`;
    const childrenLength = children.length - endIndex;

    const newNode = {
      description: 'Demo description',
      displayName: 'Load More',
      id: newNodeId,
      type: EntityLineageNodeType.LOAD_MORE,
      pagination_data: {
        index: endIndex,
        parentId: id,
        childrenLength,
      },
      edgeType: downwards ? EdgeTypeEnum.DOWN_STREAM : EdgeTypeEnum.UP_STREAM,
    };
    nodes.push(newNode);
    const newEdge: EntityLineageEdge = {
      fromEntity: downwards ? id : newNodeId,
      toEntity: downwards ? newNodeId : id,
    };
    edges.push(newEdge);
  }
};

export const getLineageChildParents = (
  obj: EntityLineage,
  nodeSet: Set<string>,
  id: string,
  isParent = false,
  index = 0
) => {
  const edges = isParent ? obj.upstreamEdges || [] : obj.downstreamEdges || [];
  const filtered = edges.filter((edge) => {
    return isParent ? edge.toEntity === id : edge.fromEntity === id;
  });

  return filtered.reduce((childMap: EntityReferenceChild[], edge, i) => {
    const node = obj.nodes?.find((node) => {
      return isParent ? node.id === edge.fromEntity : node.id === edge.toEntity;
    });

    if (node && !nodeSet.has(node.id)) {
      nodeSet.add(node.id);
      const childNodes = getLineageChildParents(
        obj,
        nodeSet,
        node.id,
        isParent,
        i
      );
      const lineage: EntityReferenceChild = { ...node, pageIndex: index + i };

      if (isParent) {
        lineage.parents = childNodes;
      } else {
        lineage.children = childNodes;
      }

      childMap.push(lineage);
    }

    return childMap;
  }, []);
};

export const removeDuplicates = (arr: EntityLineageEdge[]) => {
  return uniqWith(arr, isEqual);
};

export const nodeTypes = {
  output: CustomNodeV1,
  input: CustomNodeV1,
  default: CustomNodeV1,
  'load-more': CustomNodeV1,
};

export const customEdges = { buttonedge: CustomEdge };

export const getNewNodes = ({
  nodes,
  downstreamEdges,
  upstreamEdges,
}: EntityLineage) => {
  return nodes?.filter(
    (n) =>
      !isUndefined(downstreamEdges?.find((d) => d.toEntity === n.id)) ||
      !isUndefined(upstreamEdges?.find((u) => u.fromEntity === n.id))
  );
};

export const findNodeById = (
  id: string,
  items: EntityReferenceChild[] = [],
  path: EntityReferenceChild[] = []
): EntityReferenceChild[] | undefined => {
  for (const [index, item] of items.entries()) {
    item.pageIndex = index;
    if (item.id === id) {
      // Return the path to the item, including the item itself
      return [...path, item];
    }
    const found = findNodeById(id, item.children, [...path, item]);
    if (found) {
      return found;
    }
  }

  return undefined;
};

export const addLineageHandler = async (edge: AddLineage): Promise<void> => {
  try {
    await addLineage(edge);
  } catch (err) {
    showErrorToast(
      err as AxiosError,
      t('server.add-entity-error', {
        entity: t('label.lineage'),
      })
    );

    throw err;
  }
};

export const removeLineageHandler = async (data: EdgeData): Promise<void> => {
  try {
    await deleteLineageEdge(
      data.fromEntity,
      data.fromId,
      data.toEntity,
      data.toId
    );
  } catch (err) {
    showErrorToast(
      err as AxiosError,
      t('server.delete-entity-error', {
        entity: t('label.edge-lowercase'),
      })
    );

    throw err;
  }
};

export const getParamByEntityType = (entityType: EntityType): string => {
  switch (entityType) {
    case EntityType.TABLE:
      return 'datasetFQN';
    case EntityType.TOPIC:
      return 'topicFQN';
    case EntityType.PIPELINE:
      return 'pipelineFQN';
    case EntityType.MLMODEL:
      return 'mlModelFqn';
    case EntityType.DASHBOARD:
      return 'dashboardFQN';
    case EntityType.DATABASE:
      return 'databaseFQN';
    case EntityType.DATABASE_SCHEMA:
      return 'databaseSchemaFQN';
    case EntityType.DASHBOARD_DATA_MODEL:
      return 'dashboardDataModelFQN';
    default:
      return 'entityFQN';
  }
};

export const getEntityLineagePath = (
  entityType: EntityType,
  entityFQN: string
): string => {
  switch (entityType) {
    case EntityType.TABLE:
      return getTableTabPath(entityFQN, 'lineage');

    case EntityType.TOPIC:
      return getTopicDetailsPath(entityFQN, 'lineage');

    case EntityType.DASHBOARD:
      return getDashboardDetailsPath(entityFQN, 'lineage');

    case EntityType.PIPELINE:
      return getPipelineDetailsPath(entityFQN, 'lineage');

    case EntityType.MLMODEL:
      return getMlModelPath(entityFQN, 'lineage');

    case EntityType.DASHBOARD_DATA_MODEL:
      return getDataModelDetailsPath(entityFQN, 'lineage');

    case EntityType.CONTAINER:
      return getContainerDetailPath(entityFQN, 'lineage');

    default:
      return '';
  }
};

// Nodes Icons
export const getEntityNodeIcon = (label: string) => {
  switch (lowerCase(label)) {
    case EntityType.TABLE:
      return TableIcon;
    case EntityType.DASHBOARD:
      return DashboardIcon;
    case EntityType.TOPIC:
      return TopicIcon;
    case EntityType.PIPELINE:
      return PipelineIcon;
    case EntityType.MLMODEL:
      return MlModelIcon;
    default:
      return TableIcon;
  }
};
