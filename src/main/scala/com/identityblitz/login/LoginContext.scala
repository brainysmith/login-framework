package com.identityblitz.login

import com.identityblitz.json.{JWriter, JObj}

/**
 */
trait LoginContext {

  /**
   * 
   * @return
   */
  def callbackUri: String
  
  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  def completedMethods: Array[String]

  /**
   * Adds a specified authentication method to the methods which has already been completed successfully.
   * @param method - a name of the authentication method which has been completed.
   * @return updated login context.
   */
  def addCompletedMethod(method: String): LoginContext

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

  /**
   * Returns a string representation of this [[com.identityblitz.login.LoginContext]].
   * @return - string representation of this [[com.identityblitz.login.LoginContext]].
   */
  def asString: String

}

class LoginContextImpl extends LoginContext {

  override def callbackUri: String = ???

  override def completedMethods: Array[String] = ???

  override def addCompletedMethod(method: String): LoginContext = ???

  override def withParam[T](param: (String, T))(implicit writer: JWriter[T]): LoginContext = ???

  override def withParams(params: JObj): LoginContext = ???

  override def params: JObj = ???

  def asString: String = ???
}


object LoginContext {

  def apply(callbackUri: String): LoginContext = ???


  /**
   * Creates [[com.identityblitz.login.LoginContext]]] from string representation.
   * @param str - string representation of [[com.identityblitz.login.LoginContext]]].
   * @return - login context.
   */
  def fromString(str: String) :LoginContext = ???

}
