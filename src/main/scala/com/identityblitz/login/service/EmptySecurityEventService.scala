package com.identityblitz.login.service

import com.identityblitz.login.LoginContext
import com.identityblitz.login.error.LoginError
import com.identityblitz.login.service.spi.SecurityEventService
import com.identityblitz.login.session.LoginSession
import com.identityblitz.login.transport.InboundTransport

class EmptySecurityEventService extends SecurityEventService {
  /**
   * Process successful Logout event
   * @param ls - login session state before logout
   * @param iTr - inbound transport
   */
  override def onLogout(ls: LoginSession, iTr: InboundTransport): Unit = {}

  /**
   * Process successful Login event
   * @param ls - login session state after login
   * @param iTr - inbound transport
   */
  override def onLogin(ls: LoginSession, iTr: InboundTransport): Unit = {}

  /**
   * Process failed Login event
   * @param error - login error with params
   * @param lc - login context state after fail
   * @param iTr - inbound transport
   */
  override def onLoginFail(error: LoginError, lc: LoginContext, iTr: InboundTransport): Unit = {}
}
