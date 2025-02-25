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

import AppContainer from 'components/AppContainer/AppContainer';
import { CustomEventTypes } from 'generated/analytics/webAnalyticEventData';
import { AuthProvider } from 'generated/settings/settings';
import AccountActivationConfirmation from 'pages/signup/account-activation-confirmation.component';
import React, { useCallback, useEffect } from 'react';
import { Redirect, Route, Switch, useLocation } from 'react-router-dom';
import { useAnalytics } from 'use-analytics';
import { ROUTES } from '../../constants/constants';
import SamlCallback from '../../pages/SamlCallback';
import { isProtectedRoute } from '../../utils/AuthProvider.util';
import { useAuthContext } from '../authentication/auth-provider/AuthProvider';
import Loader from '../Loader/Loader';
import withSuspenseFallback from './withSuspenseFallback';

const SigninPage = withSuspenseFallback(
  React.lazy(() => import('pages/login'))
);
const PageNotFound = withSuspenseFallback(
  React.lazy(() => import('pages/page-not-found/PageNotFound'))
);

// const ForgotPassword = withSuspenseFallback(
//   React.lazy(() => import('pages/forgot-password/forgot-password.component'))
// );

const ResetPassword = withSuspenseFallback(
  React.lazy(() => import('pages/reset-password/reset-password.component'))
);

// const BasicSignupPage = withSuspenseFallback(
//   React.lazy(() => import('pages/signup/BasicSignup.component'))
// );

const AppRouter = () => {
  const location = useLocation();

  // web analytics instance
  const analytics = useAnalytics();

  const {
    authConfig,
    isAuthenticated,
    loading,
    isSigningIn,
    getCallBackComponent,
  } = useAuthContext();

  const callbackComponent = getCallBackComponent();
  const oidcProviders = [
    AuthProvider.Google,
    AuthProvider.AwsCognito,
    AuthProvider.CustomOidc,
  ];
  const isOidcProvider =
    authConfig?.provider && oidcProviders.includes(authConfig.provider);

  const isBasicAuthProvider =
    authConfig &&
    (authConfig.provider === AuthProvider.Basic ||
      authConfig.provider === AuthProvider.LDAP);

  useEffect(() => {
    const { pathname } = location;

    /**
     * Ignore the slash path because we are treating my data as
     * default path.
     */
    if (pathname !== '/') {
      // track page view on route change
      analytics.page();
    }
  }, [location.pathname]);

  const handleClickEvent = useCallback(
    (event: MouseEvent) => {
      const eventValue =
        (event.target as HTMLElement)?.textContent || CustomEventTypes.Click;
      if (eventValue) {
        analytics.track(eventValue);
      }
    },
    [analytics]
  );

  useEffect(() => {
    const targetNode = document.body;
    targetNode.addEventListener('click', handleClickEvent);

    return () => targetNode.removeEventListener('click', handleClickEvent);
  }, [handleClickEvent]);

  if (loading) {
    return <Loader />;
  }

  if (!isAuthenticated && isProtectedRoute(location.pathname)) {
    return <Redirect to={ROUTES.SIGNIN} />;
  }

  if (isOidcProvider || isAuthenticated) {
    return <AppContainer />;
  }

  return (
    <>
      <Switch>
        <Route exact component={SigninPage} path={ROUTES.SIGNIN} />
        {callbackComponent ? (
          <Route component={callbackComponent} path={ROUTES.CALLBACK} />
        ) : null}
        <Route component={SamlCallback} path={ROUTES.SAML_CALLBACK} />
        <Route exact path={ROUTES.HOME}>
          {!isAuthenticated && !isSigningIn ? (
            <>
              <Redirect to={ROUTES.SIGNIN} />
            </>
          ) : (
            <Redirect to={ROUTES.MY_DATA} />
          )}
        </Route>

        {isBasicAuthProvider && (
          <>
            {/* <Route exact component={BasicSignupPage} path={ROUTES.REGISTER} />*/}
            {/* <Route*/}
            {/*  exact*/}
            {/*  component={ForgotPassword}*/}
            {/*  path={ROUTES.FORGOT_PASSWORD}*/}
            {/* />*/}
            <Route
              exact
              component={ResetPassword}
              path={ROUTES.RESET_PASSWORD}
            />
            <Route
              exact
              component={AccountActivationConfirmation}
              path={ROUTES.ACCOUNT_ACTIVATION}
            />
          </>
        )}
        {isAuthenticated && <AppContainer />}
        <Route exact component={PageNotFound} path={ROUTES.NOT_FOUND} />
      </Switch>
    </>
  );
};

export default AppRouter;
