package com.identityblitz.login.service.spi

import com.identityblitz.login.LoginContext
import com.identityblitz.login.error.LoginError
import com.identityblitz.login.session.LoginSession
import com.identityblitz.login.transport.InboundTransport

/**
 * The service provides access to the configuration.
 */
trait SecurityEventService {

  /**
   * Process successful Logout event
   * @param ls - login session state before logout
   * @param iTr - inbound transport
   */
  def onLogout(ls: LoginSession, iTr: InboundTransport)

  /**
   * Process successful Login event
   * @param ls - login session state after login
   * @param iTr - inbound transport
   */
  def onLogin(ls: LoginSession, iTr: InboundTransport)

  /**
   * Process failed Login event
   * @param error - login error with params
   * @param lc - login context state after fail
   * @param iTr - inbound transport
   */
  def onLoginFail(error: LoginError, lc: LoginContext, iTr: InboundTransport)


}
