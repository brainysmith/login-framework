package com.identityblitz.login

import scala.util.Try
import com.identityblitz.login.authn.cmd.Command
import com.identityblitz.json.JObj

/**
 */
package object authn {

  type BindRes = Try[(JObj, Seq[Command])]

}
