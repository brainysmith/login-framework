package com.identityblitz.login.transport

import com.identityblitz.login.error.TransportException
import com.identityblitz.login.LoginContext
import com.identityblitz.login.Platform.Platform

/**
 * The interface represents inbound transport that brought the request.
 */
trait InboundTransport {

  /**
   * Returns parameter of the request that brought the request.
   * @param name - parameter name
   * @return - parameter value
   */
  def getParameter(name: String): Option[String]

  /**
   * Checks if the parameter with the specified name exists
   * @param name - parameter name
   * @return - false if does not exist;
   *           true if exists
   */
  def containsParameter(name: String): Boolean

  /**
   * Forwards the request to another handler. Handler is identified by the URI.
   * @param path - handler URI.
   * @throws TransportException - if any exception occurred while dispatching
   */
  @throws(classOf[TransportException])
  def forward(path: String)

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

  /**
   * Returns the current login context [[com.identityblitz.login.LoginContext]]
   * or [[scala.None]]] - the current login context is absent.
   * @return
   */
  def getLoginCtx: Option[LoginContext]

  /**
   * Updates the current login context [[com.identityblitz.login.LoginContext]]]. If the current login context is absent
   * then inserts the specified context.
   * @param loginCtx
   */
  def updatedLoginCtx(loginCtx: LoginContext)

  /**
   * Returns the attribute value corresponding to the specified name or [[scala.None]]] - if the attribute is absent.
   * The lifespan of the attribute is the same as the lifespan of the transport this attribute set on.
   * @return - attribute value
   */
  def getAttribute(name: String): Option[String]

  /**
   * Sets the attribute on the transport.
   * @param name - attribute name;
   * @param value - attribute value.
   */
  def setAttribute(name: String, value: String)

  def removeAttribute(name: String)

  def getCookie(name: String): Option[_ <: Cookie]
}
