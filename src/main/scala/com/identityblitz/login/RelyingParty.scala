package com.identityblitz.login

/**
  */
trait RelyingParty {

  def host: String

  def name: String

  def description: Option[String]

}
