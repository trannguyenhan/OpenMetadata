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

import { Button } from 'antd';
import { AxiosError } from 'axios';
import { useAuthContext } from 'components/authentication/auth-provider/AuthProvider';
import { UserProfile } from 'components/authentication/auth-provider/AuthProvider.interface';
import TeamsSelectable from 'components/TeamsSelectable/TeamsSelectable';
import { CookieStorage } from 'cookie-storage';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useHistory } from 'react-router-dom';
import { createUser } from 'rest/userAPI';
import { getNameFromUserData } from 'utils/AuthProvider.util';
import appState from '../../AppState';
import { ReactComponent as OMDLogo } from '../../assets/svg/logo-monogram.svg';
import { ELLIPSES, REDIRECT_PATHNAME, ROUTES } from '../../constants/constants';
import { CreateUser } from '../../generated/api/teams/createUser';
import { User } from '../../generated/entity/teams/user';
import { getImages, Transi18next } from '../../utils/CommonUtils';
import { showErrorToast } from '../../utils/ToastUtils';

const cookieStorage = new CookieStorage();

const SignUp = () => {
  const { t } = useTranslation();
  const history = useHistory();
  const {
    setIsSigningIn,
    jwtPrincipalClaims = [],
    authorizerConfig,
  } = useAuthContext();

  const [selectedTeams, setSelectedTeams] = useState<Array<string>>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [details, setDetails] = useState({
    displayName: appState.newUser.name || '',
    ...getNameFromUserData(
      appState.newUser as UserProfile,
      jwtPrincipalClaims,
      authorizerConfig?.principalDomain
    ),
  });

  const createNewUser = (details: User | CreateUser) => {
    setLoading(true);
    createUser(details as CreateUser)
      .then((res) => {
        if (res) {
          appState.updateUserDetails(res);
          cookieStorage.removeItem(REDIRECT_PATHNAME);
          setIsSigningIn(false);
          history.push(ROUTES.HOME);
        } else {
          setLoading(false);
        }
      })
      .catch((err: AxiosError) => {
        showErrorToast(
          err,
          t('server.create-entity-error', {
            entity: t('label.user'),
          })
        );
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const onChangeHandler = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.persist();
    setDetails((prevState) => {
      return {
        ...prevState,
        [e.target.name]: e.target.value,
      };
    });
  };

  const onSubmitHandler = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (details.name && details.displayName) {
      createNewUser({
        ...details,
        teams: selectedTeams as Array<string>,
        profile: {
          images: getImages(appState.newUser.picture ?? ''),
        },
      });
    }
  };

  return loading ? (
    <p
      className="text-center text-grey-body d-flex justify-center items-center"
      data-testid="loading-content">
      {t('label.creating-account')}
      {ELLIPSES}
    </p>
  ) : (
    // TODO: replace this with form
    <div className="d-flex justify-center">
      <div className="d-flex flex-col items-center signup-box">
        <div className="d-flex justify-center items-center">
          <OMDLogo
            data-testid="om-logo"
            height={50}
            name={t('label.open-metadata-logo')}
            width={50}
          />
        </div>
        <div>
          <h4 className="font-semibold" data-testid="om-heading">
            <Transi18next
              i18nKey="label.join-entity"
              renderElement={<span className="text-primary" />}
              values={{
                entity: t('label.open-metadata'),
              }}
            />
          </h4>
        </div>
        <div className="w-full">
          <form
            action="."
            data-testid="create-user-form"
            method="POST"
            onSubmit={onSubmitHandler}>
            <div>
              <label
                className="d-block text-body text-grey-body required-field"
                data-testid="full-name-label"
                htmlFor="displayName">
                {t('label.full-name')}
              </label>
              <input
                required
                autoComplete="off"
                data-testid="full-name-input"
                id="displayName"
                name="displayName"
                placeholder={t('label.your-entity', {
                  entity: t('label.full-name'),
                })}
                type="text"
                value={details.displayName}
                onChange={onChangeHandler}
              />
            </div>
            <div>
              <label data-testid="username-label" htmlFor="name">
                {t('label.username')}
              </label>
              <input
                readOnly
                required
                autoComplete="off"
                data-testid="username-input"
                id="name"
                name="name"
                placeholder={t('label.username')}
                type="text"
                value={details.name}
                onChange={onChangeHandler}
              />
            </div>
            <div>
              <label data-testid="email-label" htmlFor="email">
                {t('label.email')}
              </label>
              <input
                readOnly
                required
                autoComplete="off"
                data-testid="email-input"
                id="email"
                name="email"
                placeholder={t('label.your-entity', {
                  entity: `${t('label.email')} ${t('label.address')}`,
                })}
                type="email"
                value={details.email}
                onChange={onChangeHandler}
              />
            </div>
            <div>
              <label data-testid="select-team-label">
                {t('label.select-field', {
                  field: t('label.team-plural-lowercase'),
                })}
              </label>
              <TeamsSelectable
                filterJoinable
                showTeamsAlert
                onSelectionChange={setSelectedTeams}
              />
            </div>
            <div className="d-flex justify-end">
              <Button
                data-testid="create-button"
                htmlType="submit"
                type="primary">
                {t('label.create')}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default SignUp;