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

import { Tooltip } from 'antd';
import classNames from 'classnames';
import { isUndefined } from 'lodash';
import React, { FC } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useHistory } from 'react-router-dom';
import { getUserPath } from '../../../../constants/constants';
import { ThreadType } from '../../../../generated/entity/feed/thread';
import {
  entityDisplayName,
  getEntityFieldDisplay,
  prepareFeedLink,
} from '../../../../utils/FeedUtils';
import { getTaskDetailPath } from '../../../../utils/TasksUtils';
import {
  getDateTimeFromMilliSeconds,
  getDayTimeByTimeStamp,
} from '../../../../utils/TimeUtils';
import EntityPopOverCard from '../../../common/PopOverCard/EntityPopOverCard';
import UserPopOverCard from '../../../common/PopOverCard/UserPopOverCard';
import { FeedHeaderProp } from '../ActivityFeedCard.interface';
import './FeedCardHeader.style.css';

const FeedCardHeader: FC<FeedHeaderProp> = ({
  className,
  createdBy,
  timeStamp,
  entityFQN,
  entityType,
  entityField,
  isEntityFeed,
  feedType,
  task,
}) => {
  const { t } = useTranslation();
  const history = useHistory();
  const onTitleClickHandler = (name: string) => {
    history.push(getUserPath(name));
  };
  const { task: taskDetails } = task;

  const entityCheck = !isUndefined(entityFQN) && !isUndefined(entityType);

  const getFeedLinkElement = entityCheck && (
    <span data-testid="headerText">
      <span className="m-x-xss">{t('label.posted-on-lowercase')}</span>
      {isEntityFeed ? (
        <span className="font-medium" data-testid="headerText-entityField">
          {getEntityFieldDisplay(entityField)}
        </span>
      ) : (
        <>
          <span data-testid="entityType">{entityType} </span>
          <EntityPopOverCard entityFQN={entityFQN} entityType={entityType}>
            <Link
              data-testid="entitylink"
              to={prepareFeedLink(entityType, entityFQN)}>
              <span>{entityDisplayName(entityType, entityFQN)}</span>
            </Link>
          </EntityPopOverCard>
        </>
      )}
    </span>
  );

  const getTaskLinkElement = entityCheck && (
    <span>
      <span>{t('label.created-a-task-lowercase')}</span>
      <Link
        data-testid="tasklink"
        to={getTaskDetailPath(task)}
        onClick={(e) => e.stopPropagation()}>
        <span>
          {`#${taskDetails?.id} `}
          {taskDetails?.type}
        </span>
      </Link>
      <span>{t('label.for-lowercase')}</span>
      {isEntityFeed ? (
        <span data-testid="headerText-entityField">
          {getEntityFieldDisplay(entityField)}
        </span>
      ) : (
        <>
          <span>{entityType}</span>
          <EntityPopOverCard entityFQN={entityFQN} entityType={entityType}>
            <Link
              data-testid="entitylink"
              to={prepareFeedLink(entityType, entityFQN)}
              onClick={(e) => e.stopPropagation()}>
              {entityDisplayName(entityType, entityFQN)}
            </Link>
          </EntityPopOverCard>
        </>
      )}
    </span>
  );

  const getAnnouncementLinkElement = entityCheck && (
    <span>
      {t('message.made-announcement-for-entity', { entity: entityType })}{' '}
      <EntityPopOverCard entityFQN={entityFQN} entityType={entityType}>
        <Link
          data-testid="entitylink"
          to={prepareFeedLink(entityType, entityFQN)}>
          {entityDisplayName(entityType, entityFQN)}
        </Link>
      </EntityPopOverCard>
    </span>
  );

  return (
    <div className={classNames('d-inline-block', className)}>
      <UserPopOverCard userName={createdBy}>
        <span
          className="thread-author cursor-pointer"
          onClick={(e) => {
            e.stopPropagation();
            onTitleClickHandler(createdBy);
          }}>
          {createdBy}
        </span>
      </UserPopOverCard>

      {feedType === ThreadType.Conversation && getFeedLinkElement}
      {feedType === ThreadType.Task && getTaskLinkElement}
      {feedType === ThreadType.Announcement && getAnnouncementLinkElement}

      {timeStamp && (
        <Tooltip
          className="text-grey-muted"
          title={getDateTimeFromMilliSeconds(timeStamp)}>
          <span className="feed-header-timestamp" data-testid="timestamp">
            {' - ' + getDayTimeByTimeStamp(timeStamp)}
          </span>
        </Tooltip>
      )}
    </div>
  );
};

export default FeedCardHeader;
