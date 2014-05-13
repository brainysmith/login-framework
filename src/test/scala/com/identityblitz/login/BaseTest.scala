package com.identityblitz.login

import org.scalatest.{Matchers, FlatSpec}


/**
 */
class BaseTest extends FlatSpec with Matchers {

  behavior of "Flow attributes"

  it should "properly initialize a set of attributes" in {
    import FlowAttrName._
    set should be (Set(CALLBACK_URI_NAME, AUTHN_METHOD_NAME, COMMAND, COMMAND_NAME, HTTP_METHOD, ERROR, REDIRECT))
  }

}
