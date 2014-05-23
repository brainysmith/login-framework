package com.identityblitz.login.session

import com.identityblitz.json.{Json, JObj}

/**
  */
trait Session {

  /**
   * Defines login methods that have been successfully completed.
   * @return array of login methods that have been successfully completed.
   */
  val completedMethods: Seq[String]

  /**
   * Retrieve claims about the current subject.
   * @return json with claims
   */
  val claims: JObj

  /**
   * Indicates the created time.
   * @return the difference, measured in milliseconds, between the created time and midnight, January 1, 1970 UTC.
   */
  val createdOn: Long


//  def asString: String = Json.toJson(this).toJson

}


case class SessionImpl(completedMethods: Seq[String], claims: JObj, createdOn: Long) extends Session
