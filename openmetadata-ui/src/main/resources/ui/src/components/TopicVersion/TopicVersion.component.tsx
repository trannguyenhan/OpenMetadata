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

import { Col, Row, Space, Tabs, TabsProps, Tag } from 'antd';
import classNames from 'classnames';
import { CustomPropertyTable } from 'components/common/CustomPropertyTable/CustomPropertyTable';
import { CustomPropertyProps } from 'components/common/CustomPropertyTable/CustomPropertyTable.interface';
import DescriptionV1 from 'components/common/description/DescriptionV1';
import ErrorPlaceHolder from 'components/common/error-with-placeholder/ErrorPlaceHolder';
import DataAssetsVersionHeader from 'components/DataAssets/DataAssetsVersionHeader/DataAssetsVersionHeader';
import EntityVersionTimeLine from 'components/Entity/EntityVersionTimeLine/EntityVersionTimeLine';
import Loader from 'components/Loader/Loader';
import TabsLabel from 'components/TabsLabel/TabsLabel.component';
import TagsContainerV2 from 'components/Tag/TagsContainerV2/TagsContainerV2';
import TopicSchemaFields from 'components/TopicDetails/TopicSchema/TopicSchema';
import { getVersionPathWithTab } from 'constants/constants';
import { EntityField } from 'constants/Feeds.constants';
import { ERROR_PLACEHOLDER_TYPE } from 'enums/common.enum';
import { EntityTabs, EntityType } from 'enums/entity.enum';
import { TagSource } from 'generated/type/tagLabel';
import { isEmpty, noop } from 'lodash';
import React, { FC, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useHistory, useParams } from 'react-router-dom';
import { stringToHTML } from 'utils/StringsUtils';
import { getUpdatedMessageSchema } from 'utils/TopicVersionUtils';
import { ChangeDescription } from '../../generated/entity/data/topic';
import {
  getCommonExtraInfoForVersionDetails,
  getEntityVersionByField,
  getEntityVersionTags,
} from '../../utils/EntityVersionUtils';
import { TopicVersionProp } from './TopicVersion.interface';

const TopicVersion: FC<TopicVersionProp> = ({
  version,
  currentVersionData,
  isVersionLoading,
  owner,
  tier,
  slashedTopicName,
  versionList,
  deleted = false,
  backHandler,
  versionHandler,
  entityPermissions,
}: TopicVersionProp) => {
  const { t } = useTranslation();
  const history = useHistory();
  const { tab } = useParams<{ tab: EntityTabs }>();
  const [changeDescription, setChangeDescription] = useState<ChangeDescription>(
    currentVersionData.changeDescription as ChangeDescription
  );

  const { ownerDisplayName, ownerRef, tierDisplayName } = useMemo(
    () => getCommonExtraInfoForVersionDetails(changeDescription, owner, tier),
    [changeDescription, owner, tier]
  );

  const messageSchemaDiff = useMemo(
    () => getUpdatedMessageSchema(currentVersionData, changeDescription),
    [currentVersionData, changeDescription]
  );

  useEffect(() => {
    setChangeDescription(
      currentVersionData.changeDescription as ChangeDescription
    );
  }, [currentVersionData]);

  const handleTabChange = (activeKey: string) => {
    history.push(
      getVersionPathWithTab(
        EntityType.TOPIC,
        currentVersionData.fullyQualifiedName ?? '',
        String(version),
        activeKey
      )
    );
  };

  const tags = useMemo(() => {
    return getEntityVersionTags(currentVersionData, changeDescription);
  }, [currentVersionData, changeDescription]);

  const description = useMemo(() => {
    return getEntityVersionByField(
      changeDescription,
      EntityField.DESCRIPTION,
      currentVersionData.description
    );
  }, [currentVersionData, changeDescription]);

  const displayName = useMemo(() => {
    return getEntityVersionByField(
      changeDescription,
      EntityField.DISPLAYNAME,
      currentVersionData.displayName
    );
  }, [currentVersionData, changeDescription]);

  const schemaType = useMemo(() => {
    const schemaTypeDiffText = getEntityVersionByField(
      changeDescription,
      'messageSchema.schemaType',
      currentVersionData.displayName
    );

    return isEmpty(schemaTypeDiffText) ? undefined : (
      <Tag data-testid="schema-type-diff">
        {stringToHTML(schemaTypeDiffText)}
      </Tag>
    );
  }, [changeDescription, currentVersionData]);

  const tabItems: TabsProps['items'] = useMemo(
    () => [
      {
        key: EntityTabs.SCHEMA,
        label: <TabsLabel id={EntityTabs.SCHEMA} name={t('label.schema')} />,
        children: (
          <Row gutter={[0, 16]} wrap={false}>
            <Col className="p-t-sm m-x-lg" flex="auto">
              <Row gutter={[0, 16]}>
                <Col span={24}>
                  <DescriptionV1
                    isVersionView
                    description={description}
                    entityType={EntityType.TOPIC}
                  />
                </Col>
                <Col span={24}>
                  <TopicSchemaFields
                    isReadOnly
                    isVersionView
                    entityFqn={currentVersionData?.fullyQualifiedName ?? ''}
                    hasDescriptionEditAccess={false}
                    hasTagEditAccess={false}
                    messageSchema={messageSchemaDiff}
                    schemaTypePlaceholder={schemaType}
                    onThreadLinkSelect={noop}
                  />
                </Col>
              </Row>
            </Col>
            <Col
              className="entity-tag-right-panel-container"
              data-testid="entity-right-panel"
              flex="220px">
              <Space className="w-full" direction="vertical" size="large">
                {Object.keys(TagSource).map((tagType) => (
                  <TagsContainerV2
                    entityFqn={currentVersionData.fullyQualifiedName}
                    entityType={EntityType.TOPIC}
                    key={tagType}
                    permission={false}
                    selectedTags={tags}
                    tagType={TagSource[tagType as TagSource]}
                  />
                ))}
              </Space>
            </Col>
          </Row>
        ),
      },
      {
        key: EntityTabs.CUSTOM_PROPERTIES,
        label: (
          <TabsLabel
            id={EntityTabs.CUSTOM_PROPERTIES}
            name={t('label.custom-property-plural')}
          />
        ),
        children: !entityPermissions.ViewAll ? (
          <ErrorPlaceHolder type={ERROR_PLACEHOLDER_TYPE.PERMISSION} />
        ) : (
          <CustomPropertyTable
            isVersionView
            entityDetails={
              currentVersionData as CustomPropertyProps['entityDetails']
            }
            entityType={EntityType.TOPIC}
            hasEditAccess={false}
          />
        ),
      },
    ],
    [
      description,
      messageSchemaDiff,
      currentVersionData,
      entityPermissions,
      schemaType,
      tags,
    ]
  );

  return (
    <>
      {isVersionLoading ? (
        <Loader />
      ) : (
        <div className={classNames('version-data')}>
          <Row gutter={[0, 12]}>
            <Col span={24}>
              <DataAssetsVersionHeader
                breadcrumbLinks={slashedTopicName}
                currentVersionData={currentVersionData}
                deleted={deleted}
                displayName={displayName}
                entityType={EntityType.TOPIC}
                ownerDisplayName={ownerDisplayName}
                ownerRef={ownerRef}
                tierDisplayName={tierDisplayName}
                version={version}
                onVersionClick={backHandler}
              />
            </Col>
            <Col span={24}>
              <Tabs
                defaultActiveKey={tab ?? EntityTabs.SCHEMA}
                items={tabItems}
                onChange={handleTabChange}
              />
            </Col>
          </Row>
        </div>
      )}

      <EntityVersionTimeLine
        show
        currentVersion={version}
        versionHandler={versionHandler}
        versionList={versionList}
        onBack={backHandler}
      />
    </>
  );
};

export default TopicVersion;
