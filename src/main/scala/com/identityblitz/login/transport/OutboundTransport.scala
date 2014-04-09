package com.identityblitz.login.transport

import com.identityblitz.login.error.TransportException
import com.identityblitz.login.Platform._

/**
 * The interface represents outbound transport that delivers the response.
 */
trait OutboundTransport {

  /**
   * Redirect the user agent to the specified location.
   * @param location - location to redirect to.
   * @throws TransportException - if any error occurred while redirecting.
   */
  @throws(classOf[TransportException])
  def redirect(location: String)

  /**
   * Returns unwrapped transport.
   * @return - unwrapped transport.
   */
  def unwrap: AnyRef

  /**
   * Return the platform of the transport.
   * @return
   */
  def platform: Platform

 }
