package com.identityblitz.login


/**
 * Defines a flow of the login process.
 * The implementation must be a singleton.
 */
trait LoginFlow {

  /**
   * Defines a next step of the login process to redirect.
   * @return [[Some]] of a location to redirect. If result is [[None]] depending on the current login status:
   *        <ul>
   *          <li>redirect to the complete end point with resulting of the login process if the status is [[LoginStatus.SUCCESS]];</li>
   *          <li>redirect to the complete end point with error if the status is [[LoginStatus.FAIL]];</li>
   *          <li>do nothing if the status is [[LoginStatus.PROCESSING]].</li>
   *        </ul>
   */
  def next(implicit lc: LoginContext): Option[String]
}
