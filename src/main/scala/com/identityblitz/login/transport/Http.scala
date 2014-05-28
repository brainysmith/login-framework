package com.identityblitz.login.transport


/**
 * An HTTP cookie.
 */
trait Cookie {
  /**
   * @return the cookie name
   */
  def name: String

  /**
   * @return the cookie value
   */
  def value: String

  /**
   * @return the cookie expiration date in seconds, `None` for a transient cookie, or a value less than 0 to expire a cookie now
   */
  def maxAge: Option[Int]

  /**
   * @return the cookie path, defaulting to the root path `/`
   */
  def path: String

  /**
   * @return the cookie domain
   */
  def domain: Option[String]

  /**
   * @return whether this cookie is secured, sent only for HTTPS requests
   */
  def secure: Boolean

  /**
   * @return whether this cookie is HTTP only, i.e. not accessible from client-side JavaScipt code
   */
  def httpOnly: Boolean
}

trait DiscardingCookie {
  /**
   * @return the cookie name
   */
  def name: String

  /**
   * @return the cookie path, defaulting to the root path `/`
   */
  def path: String

  /**
   * @return the cookie domain
   */
  def domain: Option[String]

  /**
   * @return whether this cookie is secured, sent only for HTTPS requests
   */
  def secure: Boolean

  def toCookie: Cookie
}

case class CookieScala(name: String, value: String, maxAge: Option[Int] = None, path: String = "/",
                       domain: Option[String] = None, secure: Boolean = false, httpOnly: Boolean = true) extends Cookie

case class DiscardingCookieScala(name: String, path: String = "/", domain: Option[String] = None, secure: Boolean = false) extends DiscardingCookie {
  def toCookie = CookieScala(name, "", Some(-86400), path, domain, secure)
}


