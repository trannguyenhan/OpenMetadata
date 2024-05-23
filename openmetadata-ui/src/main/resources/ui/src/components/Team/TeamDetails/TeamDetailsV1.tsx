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

import { CheckOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Button,
  Col,
  Divider,
  Form,
  FormProps,
  Input,
  Modal,
  Row,
  Space,
  Switch,
  Tabs,
  Tooltip,
  Typography,
} from 'antd';
import { ItemType } from 'antd/lib/menu/hooks/useItems';
import { ReactComponent as IconEdit } from 'assets/svg/edit-new.svg';
import { ReactComponent as ExportIcon } from 'assets/svg/ic-export.svg';
import { ReactComponent as ImportIcon } from 'assets/svg/ic-import.svg';
import { ReactComponent as IconRestore } from 'assets/svg/ic-restore.svg';
import { ReactComponent as IconOpenLock } from 'assets/svg/open-lock.svg';
import { AxiosError } from 'axios';
import { ManageButtonItemLabel } from 'components/common/ManageButtonContentItem/ManageButtonContentItem.component';
import { OwnerLabel } from 'components/common/OwnerLabel/OwnerLabel.component';
import TableDataCardV2 from 'components/common/table-data-card-v2/TableDataCardV2';
import { useEntityExportModalProvider } from 'components/Entity/EntityExportModalProvider/EntityExportModalProvider.component';
import {
  GlobalSettingOptions,
  GlobalSettingsMenuCategory,
} from 'constants/GlobalSettings.constants';
import { DROPDOWN_ICON_SIZE_PROPS } from 'constants/ManageButton.constants';
import { EMAIL_REG_EX } from 'constants/regex.constants';
import { ERROR_PLACEHOLDER_TYPE } from 'enums/common.enum';
import { SearchIndex } from 'enums/search.enum';
import { compare } from 'fast-json-patch';
import {
  cloneDeep,
  isEmpty,
  isNil,
  isUndefined,
  lowerCase,
  uniqueId,
} from 'lodash';
import { ExtraInfo } from 'Models';
import AddAttributeModal from 'pages/RolesPage/AddAttributeModal/AddAttributeModal';
import { ImportType } from 'pages/teams/ImportTeamsPage/ImportTeamsPage.interface';
import Qs from 'qs';
import React, {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { useHistory, useLocation } from 'react-router-dom';
import { getSuggestions } from 'rest/miscAPI';
import { exportTeam, restoreTeam } from 'rest/teamsAPI';
import AppState from '../../../AppState';
import { LIST_SIZE, ROUTES } from '../../../constants/constants';
import { ROLE_DOCS, TEAMS_DOCS } from '../../../constants/docs.constants';
import { EntityAction, EntityType } from '../../../enums/entity.enum';
import { OwnerType } from '../../../enums/user.enum';
import { Operation } from '../../../generated/entity/policies/policy';
import { Team, TeamType } from '../../../generated/entity/teams/team';
import {
  EntityReference as UserTeams,
  User,
} from '../../../generated/entity/teams/user';
import { EntityReference } from '../../../generated/type/entityReference';
import { Paging } from '../../../generated/type/paging';
import {
  AddAttribute,
  PlaceholderProps,
  TeamDetailsProp,
} from '../../../interface/teamsAndUsers.interface';
import { getCountBadge, hasEditAccess } from '../../../utils/CommonUtils';
import { filterEntityAssets, getEntityName } from '../../../utils/EntityUtils';
import { checkPermission } from '../../../utils/PermissionsUtils';
import {
  getSettingsPathWithFqn,
  getTeamsWithFqnPath,
} from '../../../utils/RouterUtils';
import {
  filterChildTeams,
  getDeleteMessagePostFix,
} from '../../../utils/TeamUtils';
import { showErrorToast, showSuccessToast } from '../../../utils/ToastUtils';
import Description from '../../common/description/Description';
import ManageButton from '../../common/entityPageInfo/ManageButton/ManageButton';
import EntitySummaryDetails from '../../common/EntitySummaryDetails/EntitySummaryDetails';
import ErrorPlaceHolder from '../../common/error-with-placeholder/ErrorPlaceHolder';
import NextPrevious from '../../common/next-previous/NextPrevious';
import Searchbar from '../../common/searchbar/Searchbar';
import TitleBreadcrumb from '../../common/title-breadcrumb/title-breadcrumb.component';
import { TitleBreadcrumbProps } from '../../common/title-breadcrumb/title-breadcrumb.interface';
import Loader from '../../Loader/Loader';
import ConfirmationModal from '../../Modals/ConfirmationModal/ConfirmationModal';
import { usePermissionProvider } from '../../PermissionProvider/PermissionProvider';
import { ResourceEntity } from '../../PermissionProvider/PermissionProvider.interface';
import ListEntities from './RolesAndPoliciesList';
import { TeamsPageTab } from './team.interface';
import { getTabs } from './TeamDetailsV1.utils';
import TeamHierarchy from './TeamHierarchy';
import './teams.less';
import { UserTab } from './UserTab/UserTab.component';

const TeamDetailsV1 = ({
  assets,
  hasAccess,
  currentTeam,
  currentTeamUsers,
  teamUserPaging,
  currentTeamUserPage,
  teamUsersSearchText,
  isDescriptionEditable,
  isTeamMemberLoading,
  childTeams,
  onTeamExpand,
  handleAddTeam,
  updateTeamHandler,
  onDescriptionUpdate,
  descriptionHandler,
  showDeletedTeam,
  onShowDeletedTeamChange,
  handleTeamUsersSearchAction,
  handleCurrentUserPage,
  teamUserPagingHandler,
  handleJoinTeamClick,
  handleLeaveTeamClick,
  handleAddUser,
  removeUserFromTeam,
  afterDeleteAction,
  onAssetsPaginate,
  parentTeams,
  entityPermissions,
  isFetchingAdvancedDetails,
  isFetchingAllTeamAdvancedDetails,
}: TeamDetailsProp) => {
  const { t } = useTranslation();
  const history = useHistory();
  const location = useLocation();

  const { activeTab } = useMemo(() => {
    const param = location.search;
    const searchData = Qs.parse(
      param.startsWith('?') ? param.substring(1) : param
    );

    return searchData as { activeTab: TeamsPageTab };
  }, [location.search]);
  const isOrganization = currentTeam.name === TeamType.Organization;
  const isGroupType = currentTeam.teamType === TeamType.Group;
  const DELETE_USER_INITIAL_STATE = {
    user: undefined,
    state: false,
    leave: false,
  };
  const { permissions } = usePermissionProvider();
  const currentTab = useMemo(() => {
    if (activeTab) {
      return activeTab;
    }

    return isGroupType ? TeamsPageTab.USERS : TeamsPageTab.TEAMS;
  }, [activeTab, isGroupType]);
  const [isHeadingEditing, setIsHeadingEditing] = useState(false);
  const [currentUser, setCurrentUser] = useState<User>();
  const [heading, setHeading] = useState(
    currentTeam ? currentTeam.displayName : ''
  );
  const [deletingUser, setDeletingUser] = useState<{
    user: UserTeams | undefined;
    state: boolean;
    leave: boolean;
  }>(DELETE_USER_INITIAL_STATE);
  const [searchTerm, setSearchTerm] = useState('');
  const [childTeamList, setChildTeamList] = useState<Team[]>([]);
  const [slashedTeamName, setSlashedTeamName] = useState<
    TitleBreadcrumbProps['titleLinks']
  >([]);
  const [addAttribute, setAddAttribute] = useState<AddAttribute>();
  const [selectedEntity, setEntity] = useState<{
    attribute: 'defaultRoles' | 'policies';
    record: EntityReference;
  }>();
  const [isModalLoading, setIsModalLoading] = useState<boolean>(false);
  const [isEmailEdit, setIsEmailEdit] = useState<boolean>(false);
  const { showModal } = useEntityExportModalProvider();

  const addPolicy = t('label.add-entity', {
    entity: t('label.policy'),
  });

  const addRole = t('label.add-entity', {
    entity: t('label.role'),
  });

  const addTeam = t('label.add-entity', { entity: t('label.team') });

  const teamCount = useMemo(
    () =>
      isOrganization && currentTeam && currentTeam.childrenCount
        ? currentTeam.childrenCount + 1
        : childTeamList.length,
    [childTeamList, isOrganization, currentTeam.childrenCount]
  );
  const updateActiveTab = (key: string) => {
    history.push({ search: Qs.stringify({ activeTab: key }) });
  };

  const tabs = useMemo(() => {
    const allTabs = getTabs(
      currentTeam,
      isGroupType,
      isOrganization,
      teamCount
    ).map((tab) => ({
      ...tab,
      label: (
        <div data-testid={`${lowerCase(tab.key)}-tab`}>
          {tab.name}
          <span className="p-l-xs">
            {!isNil(tab.count)
              ? getCountBadge(tab.count, '', currentTab === tab.key)
              : getCountBadge()}
          </span>
        </div>
      ),
    }));

    return allTabs;
  }, [currentTeam, teamUserPaging, searchTerm, teamCount, currentTab]);

  const createTeamPermission = useMemo(
    () =>
      !isEmpty(permissions) &&
      checkPermission(Operation.Create, ResourceEntity.TEAM, permissions),
    [permissions]
  );

  /**
   * Check if current team is the owner or not
   * @returns - True true or false based on hasEditAccess response
   */
  const isOwner = () => {
    return hasEditAccess(
      currentTeam?.owner?.type || '',
      currentTeam?.owner?.id || ''
    );
  };

  /**
   * Take user id as input to find out the user data and set it for delete
   * @param id - user id
   * @param leave - if "Leave Team" action is in progress
   */
  const deleteUserHandler = (id: string, leave = false) => {
    const user = [...(currentTeam?.users as Array<UserTeams>)].find(
      (u) => u.id === id
    );
    setDeletingUser({ user, state: true, leave });
  };

  const fetchErrorPlaceHolder = useCallback(
    ({
      permission,
      onClick,
      heading,
      doc,
      button,
      children,
      type = ERROR_PLACEHOLDER_TYPE.CREATE,
    }: PlaceholderProps) => (
      <ErrorPlaceHolder
        button={button}
        className="mt-0-important"
        doc={doc}
        heading={heading}
        permission={permission}
        type={type}
        onClick={onClick}>
        {children}
      </ErrorPlaceHolder>
    ),
    []
  );

  const extraInfo: ExtraInfo[] = [
    ...(isOrganization
      ? []
      : [
          {
            key: 'TeamType',
            value: currentTeam.teamType || '',
          },
        ]),
  ];

  const searchTeams = async (text: string) => {
    try {
      const res = await getSuggestions<SearchIndex.TEAM>(
        text,
        SearchIndex.TEAM
      );
      const data = res.data.suggest['metadata-suggest'][0].options.map(
        (value) => value._source as Team
      );

      setChildTeamList(data);
    } catch (error) {
      setChildTeamList([]);
    }
  };

  const isActionAllowed = (operation = false) => {
    return hasAccess || isOwner() || operation;
  };

  const handleOpenToJoinToggle = () => {
    if (currentTeam) {
      const updatedData: Team = {
        ...currentTeam,
        isJoinable: !currentTeam.isJoinable,
      };
      updateTeamHandler(updatedData, false);
    }
  };

  const isAlreadyJoinedTeam = (teamId: string) => {
    if (currentUser) {
      return currentUser.teams?.find((team) => team.id === teamId);
    }

    return false;
  };

  const handleHeadingSave = () => {
    if (heading && currentTeam) {
      const updatedData: Team = {
        ...currentTeam,
        displayName: heading,
      };

      updateTeamHandler(updatedData);
      setIsHeadingEditing(false);
    }
  };

  const joinTeam = () => {
    if (currentUser && currentTeam) {
      const newTeams = cloneDeep(currentUser.teams ?? []);
      newTeams.push({
        id: currentTeam.id,
        type: OwnerType.TEAM,
        name: currentTeam.name,
      });

      const updatedData: User = {
        ...currentUser,
        teams: newTeams,
      };

      const options = compare(currentUser, updatedData);

      handleJoinTeamClick(currentUser.id, options);
    }
  };

  const leaveTeam = (): Promise<void> => {
    if (currentUser && currentTeam) {
      let newTeams = cloneDeep(currentUser.teams ?? []);
      newTeams = newTeams.filter((team) => team.id !== currentTeam.id);

      const updatedData: User = {
        ...currentUser,
        teams: newTeams,
      };

      const options = compare(currentUser, updatedData);

      return handleLeaveTeamClick(currentUser.id, options);
    }

    return Promise.reject();
  };

  const handleRemoveUser = () => {
    if (deletingUser.leave) {
      leaveTeam().then(() => {
        setDeletingUser(DELETE_USER_INITIAL_STATE);
      });
    } else {
      removeUserFromTeam(deletingUser.user?.id as string).then(() => {
        setDeletingUser(DELETE_USER_INITIAL_STATE);
      });
    }
  };

  const updateOwner = useCallback(
    (owner?: EntityReference) => {
      if (currentTeam) {
        const updatedData: Team = {
          ...currentTeam,
          owner,
        };

        return updateTeamHandler(updatedData);
      }

      return Promise.reject();
    },
    [currentTeam]
  );

  const updateTeamType = (type: TeamType) => {
    if (currentTeam) {
      const updatedData: Team = {
        ...currentTeam,
        teamType: type,
      };

      return updateTeamHandler(updatedData);
    }

    return;
  };

  const handleTeamSearch = (value: string) => {
    setSearchTerm(value);
    if (value) {
      searchTeams(value);
    } else {
      setChildTeamList(filterChildTeams(childTeams ?? [], showDeletedTeam));
    }
  };

  const handleAddAttribute = async (selectedIds: string[]) => {
    if (addAttribute) {
      setIsModalLoading(true);
      let updatedTeamData = { ...currentTeam };
      const updatedData = selectedIds.map((id) => {
        const existingData = addAttribute.selectedData.find(
          (data) => data.id === id
        );

        return existingData ? existingData : { id, type: addAttribute.type };
      });

      switch (addAttribute.type) {
        case EntityType.ROLE:
          updatedTeamData = { ...updatedTeamData, defaultRoles: updatedData };

          break;

        case EntityType.POLICY:
          updatedTeamData = { ...updatedTeamData, policies: updatedData };

          break;

        default:
          break;
      }
      await updateTeamHandler(updatedTeamData);
      setAddAttribute(undefined);
      setIsModalLoading(false);
    }
  };

  const handleAttributeDelete = async (
    record: EntityReference,
    attribute: 'defaultRoles' | 'policies'
  ) => {
    setIsModalLoading(true);
    const attributeData =
      (currentTeam[attribute as keyof Team] as EntityReference[]) ?? [];
    const updatedAttributeData = attributeData.filter(
      (attrData) => attrData.id !== record.id
    );

    const updatedTeamData = {
      ...currentTeam,
      [attribute]: updatedAttributeData,
    };
    await updateTeamHandler(updatedTeamData);
    setIsModalLoading(false);
  };

  const handleReactiveTeam = async () => {
    try {
      const res = await restoreTeam(currentTeam.id);
      if (res) {
        afterDeleteAction();
        showSuccessToast(
          t('message.entity-restored-success', {
            entity: t('label.team'),
          })
        );
      } else {
        throw t('message.entity-restored-error', {
          entity: t('label.team'),
        });
      }
    } catch (error) {
      showErrorToast(
        error as AxiosError,
        t('message.entity-restored-error', {
          entity: t('label.team'),
        })
      );
    }
  };

  const handleUpdateEmail: FormProps['onFinish'] = (values) => {
    const { email } = values;
    if (currentTeam) {
      const updatedData: Team = {
        ...currentTeam,
        email: isEmpty(email) ? undefined : email,
      };

      updateTeamHandler(updatedData);
      setIsEmailEdit(false);
    }
  };

  useEffect(() => {
    if (currentTeam) {
      const parents =
        parentTeams && !isOrganization
          ? parentTeams.map((parent) => ({
              name: getEntityName(parent),
              url: getTeamsWithFqnPath(
                parent.name || parent.fullyQualifiedName || ''
              ),
            }))
          : [];
      const breadcrumb = [
        ...parents,
        {
          name: getEntityName(currentTeam),
          url: '',
        },
      ];
      setSlashedTeamName(breadcrumb);
      setHeading(currentTeam.displayName || currentTeam.name);
    }
  }, [currentTeam, parentTeams, showDeletedTeam]);

  useEffect(() => {
    setChildTeamList(filterChildTeams(childTeams ?? [], showDeletedTeam));
    setSearchTerm('');
  }, [childTeams, showDeletedTeam]);

  useEffect(() => {
    setCurrentUser(AppState.getCurrentUserDetails());
  }, [currentTeam, AppState.userDetails, AppState.nonSecureUserDetails]);

  useEffect(() => {
    handleCurrentUserPage();
  }, []);

  const removeUserBodyText = (leave: boolean) => {
    const text = leave
      ? t('message.leave-the-team-team-name', {
          teamName: currentTeam?.displayName ?? currentTeam?.name,
        })
      : t('label.remove-entity', {
          entity: deletingUser.user?.displayName ?? deletingUser.user?.name,
        });

    return t('message.are-you-sure-want-to-text', { text });
  };

  const restoreIcon = useMemo(
    () => (
      <IconRestore {...DROPDOWN_ICON_SIZE_PROPS} name={t('label.restore')} />
    ),
    [currentTeam.isJoinable]
  );

  const handleTeamExportClick = useCallback(async () => {
    if (currentTeam?.name) {
      showModal({
        name: currentTeam?.name,
        onExport: exportTeam,
      });
    }
  }, [currentTeam]);
  const handleImportClick = useCallback(async () => {
    history.push({
      pathname: getSettingsPathWithFqn(
        GlobalSettingsMenuCategory.MEMBERS,
        GlobalSettingOptions.TEAMS,
        currentTeam.name,
        EntityAction.IMPORT
      ),
      search: Qs.stringify({ type: ImportType.TEAMS }),
    });
  }, []);

  const IMPORT_EXPORT_MENU_ITEM = useMemo(() => {
    const options = [
      {
        label: (
          <ManageButtonItemLabel
            description={t('message.export-entity-help', {
              entity: t('label.team-lowercase'),
            })}
            icon={<ExportIcon width="18px" />}
            id="export"
            name={t('label.export')}
          />
        ),

        onClick: handleTeamExportClick,
        key: 'export-button',
      },
    ];

    if (entityPermissions.Create) {
      options.push({
        label: (
          <ManageButtonItemLabel
            description={t('message.import-entity-help', {
              entity: t('label.team-lowercase'),
            })}
            icon={<ImportIcon width="20px" />}
            id="import-button"
            name={t('label.import')}
          />
        ),
        onClick: handleImportClick,
        key: 'import-button',
      });
    }

    return options;
  }, [handleImportClick, handleTeamExportClick, entityPermissions]);

  const extraDropdownContent: ItemType[] = useMemo(
    () => [
      // ...(isGroupType ? [] : IMPORT_EXPORT_MENU_ITEM),
      ...(true ? [] : IMPORT_EXPORT_MENU_ITEM),
      ...(!currentTeam.parents?.[0]?.deleted && currentTeam.deleted
        ? [
            {
              label: (
                <ManageButtonItemLabel
                  description={t('message.restore-deleted-team')}
                  icon={restoreIcon}
                  id="restore-team-dropdown"
                  name={t('label.restore-entity', {
                    entity: t('label.team'),
                  })}
                />
              ),
              onClick: handleReactiveTeam,
              key: 'restore-team-dropdown',
            },
          ]
        : []),
      {
        label: (
          <ManageButtonItemLabel
            description={t('message.access-to-collaborate')}
            icon={<IconOpenLock {...DROPDOWN_ICON_SIZE_PROPS} />}
            id="open-group-dropdown"
            name={
              <Row>
                <Col span={21}>
                  <Typography.Text
                    className="font-medium"
                    data-testid="open-group-label">
                    {t('label.public-team')}
                  </Typography.Text>
                </Col>

                <Col span={3}>
                  <Switch checked={currentTeam.isJoinable} size="small" />
                </Col>
              </Row>
            }
          />
        ),
        onClick: handleOpenToJoinToggle,
        key: 'open-group-dropdown',
      },
    ],
    [
      entityPermissions,
      currentTeam,
      childTeams,
      showDeletedTeam,
      handleTeamExportClick,
    ]
  );

  /**
   * Check for current team datasets and return the dataset cards
   * @returns - dataset cards
   */
  const getAssetDetailCards = () => {
    const ownData = filterEntityAssets(currentTeam?.owns || []);

    if (isEmpty(ownData)) {
      return fetchErrorPlaceHolder({
        type: ERROR_PLACEHOLDER_TYPE.ASSIGN,
        heading: t('label.asset'),
        permission: entityPermissions.EditAll,
        button: (
          <Button
            ghost
            className="p-x-lg"
            data-testid="add-placeholder-button"
            icon={<PlusOutlined />}
            type="primary"
            onClick={() => history.push(ROUTES.EXPLORE)}>
            {t('label.add')}
          </Button>
        ),
      });
    }

    return (
      <div data-testid="table-container">
        {assets.data.map(({ _source, _id = '' }, index) => (
          <TableDataCardV2
            className="m-b-sm cursor-pointer"
            id={_id}
            key={index}
            source={_source}
          />
        ))}
        {assets.total > LIST_SIZE && assets.data.length > 0 && (
          <NextPrevious
            isNumberBased
            currentPage={assets.currPage}
            pageSize={LIST_SIZE}
            paging={{} as Paging}
            pagingHandler={onAssetsPaginate}
            totalCount={assets.total}
          />
        )}
      </div>
    );
  };

  const teamActionButton = (alreadyJoined: boolean, isJoinable: boolean) => {
    return alreadyJoined ? (
      isJoinable || hasAccess ? (
        <Button data-testid="join-teams" type="primary" onClick={joinTeam}>
          {t('label.join-team')}
        </Button>
      ) : null
    ) : (
      <Button
        ghost
        data-testid="leave-team-button"
        type="primary"
        onClick={() => currentUser && deleteUserHandler(currentUser.id, true)}>
        {t('label.leave-team')}
      </Button>
    );
  };

  const getTeamHeading = () => {
    return (
      <div className="text-link-color text-base">
        {isHeadingEditing ? (
          <Space size="middle">
            <Input
              className="w-64"
              data-testid="synonyms"
              id="synonyms"
              name="synonyms"
              placeholder={t('message.enter-comma-separated-field', {
                field: t('label.term-lowercase'),
              })}
              type="text"
              value={heading}
              onChange={(e) => setHeading(e.target.value)}
            />
            <Space data-testid="buttons">
              <Button
                className="rounded-4 text-sm p-xss"
                data-testid="cancelAssociatedTag"
                type="primary"
                onMouseDown={() => setIsHeadingEditing(false)}>
                <CloseOutlined />
              </Button>
              <Button
                className="rounded-4 text-sm p-xss"
                data-testid="saveAssociatedTag"
                type="primary"
                onMouseDown={handleHeadingSave}>
                <CheckOutlined />
              </Button>
            </Space>
          </Space>
        ) : (
          <Space align="start" data-testid="team-heading">
            <Typography.Title
              className="m-b-0"
              ellipsis={{ rows: 1, tooltip: true }}
              level={5}>
              {heading}
            </Typography.Title>
            {isActionAllowed() && (
              <Tooltip
                placement="right"
                title={
                  entityPermissions.EditAll || entityPermissions.EditDisplayName
                    ? t('label.edit-entity', {
                        entity: t('label.display-name'),
                      })
                    : t('message.no-permission-for-action')
                }>
                <Button
                  className="p-0"
                  data-testid="edit-synonyms"
                  disabled={
                    !(
                      entityPermissions.EditDisplayName ||
                      entityPermissions.EditAll
                    )
                  }
                  icon={<IconEdit height={16} width={16} />}
                  size="small"
                  type="text"
                  onClick={() => setIsHeadingEditing(true)}
                />
              </Tooltip>
            )}
          </Space>
        )}
      </div>
    );
  };

  const emailElement = useMemo(
    () => (
      <Space align="start" className="m-y-xs">
        {isEmailEdit ? (
          <Form
            initialValues={{ email: currentTeam.email }}
            onFinish={handleUpdateEmail}>
            <Space align="baseline" size="middle">
              <Form.Item
                className="m-b-0"
                name="email"
                rules={[
                  {
                    pattern: EMAIL_REG_EX,
                    type: 'email',
                    message: t('message.field-text-is-invalid', {
                      fieldText: t('label.email'),
                    }),
                  },
                ]}>
                <Input
                  className="w-64"
                  data-testid="email-input"
                  placeholder={t('label.enter-entity', {
                    entity: t('label.email-lowercase'),
                  })}
                />
              </Form.Item>
              <Space>
                <Button
                  className="h-8 p-x-xss"
                  data-testid="cancel-edit-email"
                  size="small"
                  type="primary"
                  onClick={() => setIsEmailEdit(false)}>
                  <CloseOutlined />
                </Button>
                <Button
                  className="h-8 p-x-xss"
                  data-testid="save-edit-email"
                  htmlType="submit"
                  size="small"
                  type="primary">
                  <CheckOutlined />
                </Button>
              </Space>
            </Space>
          </Form>
        ) : (
          <>
            <Typography.Text data-testid="email-value">
              {currentTeam.email ||
                t('label.no-entity', { entity: t('label.email') })}
            </Typography.Text>
            <Tooltip
              placement="right"
              title={
                entityPermissions.EditAll
                  ? t('label.edit-entity', {
                      entity: t('label.email'),
                    })
                  : t('message.no-permission-for-action')
              }>
              <Button
                data-testid="edit-email"
                disabled={!entityPermissions.EditAll}
                icon={<IconEdit height={16} width={16} />}
                size="small"
                type="text"
                onClick={() => setIsEmailEdit(true)}
              />
            </Tooltip>
          </>
        )}
      </Space>
    ),
    [isEmailEdit, currentTeam, entityPermissions]
  );

  if (isTeamMemberLoading > 0) {
    return <Loader />;
  }

  return (
    <div
      className="h-full d-flex flex-col flex-grow"
      data-testid="team-details-container">
      {!isEmpty(currentTeam) ? (
        <Fragment>
          {!isOrganization && (
            <TitleBreadcrumb className="p-b-xs" titleLinks={slashedTeamName} />
          )}
          <div
            className="d-flex justify-between items-center"
            data-testid="header">
            {getTeamHeading()}
            {!isOrganization ? (
              <Space align="center">
                {!isUndefined(currentUser) &&
                  teamActionButton(
                    !isAlreadyJoinedTeam(currentTeam.id),
                    currentTeam.isJoinable || false
                  )}
                {entityPermissions.EditAll && (
                  <ManageButton
                    isRecursiveDelete
                    afterDeleteAction={afterDeleteAction}
                    allowSoftDelete={!currentTeam.deleted}
                    canDelete={entityPermissions.EditAll}
                    entityId={currentTeam.id}
                    entityName={
                      currentTeam.fullyQualifiedName || currentTeam.name
                    }
                    entityType="team"
                    extraDropdownContent={extraDropdownContent}
                    hardDeleteMessagePostFix={getDeleteMessagePostFix(
                      currentTeam.fullyQualifiedName || currentTeam.name,
                      t('label.permanently-lowercase')
                    )}
                    softDeleteMessagePostFix={getDeleteMessagePostFix(
                      currentTeam.fullyQualifiedName || currentTeam.name,
                      t('label.soft-lowercase')
                    )}
                  />
                )}
              </Space>
            ) : (
              // <ManageButton
              //   canDelete={false}
              //   entityName={currentTeam.fullyQualifiedName ?? currentTeam.name}
              //   extraDropdownContent={[...IMPORT_EXPORT_MENU_ITEM]}
              // />
              <Space>
              </Space>
            )}
          </div>
          {emailElement}
          <Space size={4}>
            <OwnerLabel
              hasPermission={hasAccess}
              owner={currentTeam?.owner}
              onUpdate={updateOwner}
            />
            {!isOrganization && <Divider type="vertical" />}

            {extraInfo.map((info) => (
              <Fragment key={uniqueId()}>
                <EntitySummaryDetails
                  allowTeamOwner={false}
                  currentOwner={currentTeam.owner}
                  data={info}
                  isGroupType={isGroupType}
                  showGroupOption={!childTeams.length}
                  teamType={currentTeam.teamType}
                  updateOwner={
                    entityPermissions.EditAll || entityPermissions.EditOwner
                      ? updateOwner
                      : undefined
                  }
                  updateTeamType={
                    entityPermissions.EditAll ? updateTeamType : undefined
                  }
                />
              </Fragment>
            ))}
          </Space>
          <div className="m-b-sm m-t-xs" data-testid="description-container">
            <Description
              description={currentTeam?.description || ''}
              entityName={currentTeam?.displayName ?? currentTeam?.name}
              hasEditAccess={
                entityPermissions.EditDescription || entityPermissions.EditAll
              }
              isEdit={isDescriptionEditable}
              onCancel={() => descriptionHandler(false)}
              onDescriptionEdit={() => descriptionHandler(true)}
              onDescriptionUpdate={onDescriptionUpdate}
            />
          </div>

          <div className="d-flex flex-col flex-grow">
            <Tabs
              defaultActiveKey={currentTab}
              items={tabs}
              onChange={updateActiveTab}
            />
            {isFetchingAdvancedDetails ? (
              <Loader />
            ) : (
              <div className="flex-grow d-flex flex-col">
                {currentTab === TeamsPageTab.TEAMS &&
                  (currentTeam.childrenCount === 0 && !searchTerm ? (
                    fetchErrorPlaceHolder({
                      onClick: () => handleAddTeam(true),
                      permission: createTeamPermission,
                      heading: t('label.team'),
                    })
                  ) : (
                    <Row
                      className="team-list-container"
                      gutter={[8, 16]}
                      justify="space-between">
                      <Col span={8}>
                        <Searchbar
                          removeMargin
                          placeholder={t('label.search-entity', {
                            entity: t('label.team'),
                          })}
                          searchValue={searchTerm}
                          typingInterval={500}
                          onSearch={handleTeamSearch}
                        />
                      </Col>
                      <Col>
                        <Space align="center">
                          <span>
                            <Switch
                              checked={showDeletedTeam}
                              data-testid="show-deleted"
                              onClick={onShowDeletedTeamChange}
                            />
                            <Typography.Text className="m-l-xs">
                              {t('label.deleted')}
                            </Typography.Text>
                          </span>

                          {createTeamPermission && (
                            <Button
                              data-testid="add-team"
                              title={
                                createTeamPermission
                                  ? addTeam
                                  : t('message.no-permission-for-action')
                              }
                              type="primary"
                              onClick={() => handleAddTeam(true)}>
                              {addTeam}
                            </Button>
                          )}
                        </Space>
                      </Col>
                      <Col span={24}>
                        <TeamHierarchy
                          currentTeam={currentTeam}
                          data={childTeamList}
                          isFetchingAllTeamAdvancedDetails={
                            isFetchingAllTeamAdvancedDetails
                          }
                          onTeamExpand={onTeamExpand}
                        />
                      </Col>
                    </Row>
                  ))}

                {currentTab === TeamsPageTab.USERS && (
                  <UserTab
                    currentPage={currentTeamUserPage}
                    currentTeam={currentTeam}
                    isLoading={isTeamMemberLoading}
                    paging={teamUserPaging}
                    permission={entityPermissions}
                    searchText={teamUsersSearchText}
                    users={currentTeamUsers}
                    onAddUser={handleAddUser}
                    onChangePaging={teamUserPagingHandler}
                    onRemoveUser={removeUserFromTeam}
                    onSearchUsers={handleTeamUsersSearchAction}
                  />
                )}

                {currentTab === TeamsPageTab.ASSETS && getAssetDetailCards()}

                {currentTab === TeamsPageTab.ROLES &&
                  (isEmpty(currentTeam.defaultRoles || []) ? (
                    fetchErrorPlaceHolder({
                      permission: entityPermissions.EditAll,
                      heading: t('label.role'),
                      doc: ROLE_DOCS,
                      children: t('message.assigning-team-entity-description', {
                        entity: t('label.role'),
                        name: currentTeam.name,
                      }),
                      type: ERROR_PLACEHOLDER_TYPE.ASSIGN,
                      button: (
                        <Button
                          ghost
                          className="p-x-lg"
                          data-testid="add-placeholder-button"
                          icon={<PlusOutlined />}
                          type="primary"
                          onClick={() =>
                            setAddAttribute({
                              type: EntityType.ROLE,
                              selectedData: currentTeam.defaultRoles || [],
                            })
                          }>
                          {t('label.add')}
                        </Button>
                      ),
                    })
                  ) : (
                    <Space
                      className="tw-w-full roles-and-policy"
                      direction="vertical">
                      <Button
                        data-testid="add-role"
                        disabled={!entityPermissions.EditAll}
                        title={
                          entityPermissions.EditAll
                            ? addRole
                            : t('message.no-permission-for-action')
                        }
                        type="primary"
                        onClick={() =>
                          setAddAttribute({
                            type: EntityType.ROLE,
                            selectedData: currentTeam.defaultRoles || [],
                          })
                        }>
                        {addRole}
                      </Button>
                      <ListEntities
                        hasAccess={entityPermissions.EditAll}
                        list={currentTeam.defaultRoles || []}
                        type={EntityType.ROLE}
                        onDelete={(record) =>
                          setEntity({ record, attribute: 'defaultRoles' })
                        }
                      />
                    </Space>
                  ))}
                {currentTab === TeamsPageTab.POLICIES &&
                  (isEmpty(currentTeam.policies) ? (
                    fetchErrorPlaceHolder({
                      permission: entityPermissions.EditAll,
                      children: t('message.assigning-team-entity-description', {
                        entity: t('label.policy-plural'),
                        name: currentTeam.name,
                      }),
                      type: ERROR_PLACEHOLDER_TYPE.ASSIGN,
                      button: (
                        <Button
                          ghost
                          className="p-x-lg"
                          data-testid="add-placeholder-button"
                          icon={<PlusOutlined />}
                          type="primary"
                          onClick={() =>
                            setAddAttribute({
                              type: EntityType.POLICY,
                              selectedData: currentTeam.policies || [],
                            })
                          }>
                          {t('label.add')}
                        </Button>
                      ),
                    })
                  ) : (
                    <Space
                      className="tw-w-full roles-and-policy"
                      direction="vertical">
                      <Button
                        data-testid="add-policy"
                        disabled={!entityPermissions.EditAll}
                        title={
                          entityPermissions.EditAll
                            ? addPolicy
                            : t('message.no-permission-for-action')
                        }
                        type="primary"
                        onClick={() =>
                          setAddAttribute({
                            type: EntityType.POLICY,
                            selectedData: currentTeam.policies || [],
                          })
                        }>
                        {addPolicy}
                      </Button>
                      <ListEntities
                        hasAccess={entityPermissions.EditAll}
                        list={currentTeam.policies || []}
                        type={EntityType.POLICY}
                        onDelete={(record) =>
                          setEntity({ record, attribute: 'policies' })
                        }
                      />
                    </Space>
                  ))}
              </div>
            )}
          </div>
        </Fragment>
      ) : (
        fetchErrorPlaceHolder({
          onClick: () => handleAddTeam(true),
          permission: createTeamPermission,
          heading: t('label.team-plural'),
          doc: TEAMS_DOCS,
        })
      )}

      <ConfirmationModal
        bodyText={removeUserBodyText(deletingUser.leave)}
        cancelText={t('label.cancel')}
        confirmText={t('label.confirm')}
        header={
          deletingUser.leave ? t('label.leave-team') : t('label.removing-user')
        }
        visible={deletingUser.state}
        onCancel={() => setDeletingUser(DELETE_USER_INITIAL_STATE)}
        onConfirm={handleRemoveUser}
      />

      {addAttribute && (
        <AddAttributeModal
          isModalLoading={isModalLoading}
          isOpen={!isUndefined(addAttribute)}
          selectedKeys={addAttribute.selectedData.map((data) => data.id)}
          title={`${t('label.add')} ${addAttribute.type}`}
          type={addAttribute.type}
          onCancel={() => setAddAttribute(undefined)}
          onSave={(data) => handleAddAttribute(data)}
        />
      )}
      {selectedEntity && (
        <Modal
          centered
          closable={false}
          confirmLoading={isModalLoading}
          maskClosable={false}
          okText={t('label.confirm')}
          open={!isUndefined(selectedEntity.record)}
          title={`${t('label.remove-entity', {
            entity: getEntityName(selectedEntity?.record),
          })} ${t('label.from-lowercase')} ${getEntityName(currentTeam)}`}
          onCancel={() => setEntity(undefined)}
          onOk={async () => {
            await handleAttributeDelete(
              selectedEntity.record,
              selectedEntity.attribute
            );
            setEntity(undefined);
          }}>
          <Typography.Text>
            {t('message.are-you-sure-you-want-to-remove-child-from-parent', {
              child: getEntityName(selectedEntity.record),
              parent: getEntityName(currentTeam),
            })}
          </Typography.Text>
        </Modal>
      )}
    </div>
  );
};

export default TeamDetailsV1;
