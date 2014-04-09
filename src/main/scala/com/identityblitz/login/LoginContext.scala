package com.identityblitz.login

import com.identityblitz.json.{JWriter, JObj}

/**
 */
trait LoginContext {

  /**
   * 
   * @return
   */
  def redirectUri: String
  
  /**
   * Defines the current status of the login process.
   * See the possible statuses in [[LoginStatus]].
   * @return the current status of the login process.
   */
  def status: Int

  /**
   * Defines the current processing authentication method.
   * @return the current authentication method
   */
  def method: String

  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  def completedMethods: Array[String]

  /**
   * Defines the parameters associated with the current login process.
   * @return json parameters.
   */
  def params: JObj

  /**
   * Adds a specified parameter to the current parameters. If the parameter with the same name already exists it is
   * overwritten.
   * @param param the tuple with the parameter name and value.
   * @tparam T type of the parameter value, for example: Int, Boolean, String and others type of the JSON.
   * @return the current login context.
   */
  def withParam[T](param: (String, T))(implicit writer: JWriter[T]): LoginContext

  /**
   * Add a specified parameters to the current parameters. All parameters with the same name will be overwritten.
   * @param params the json object with additional parameters.
   * @return the current login context.
   */
  def withParams(params: JObj): LoginContext
}

class LoginContextImpl extends LoginContext {

  override def redirectUri: String = ???

  override def status: Int = ???

  override def method: String = ???

  override def completedMethods: Array[String] = ???

  override def withParam[T](param: (String, T))(implicit writer: JWriter[T]): LoginContext = ???

  override def withParams(params: JObj): LoginContext = ???

  override def params: JObj = ???
}


object LoginContext {

  def apply(redirectUri: String): LoginContext = ???

}