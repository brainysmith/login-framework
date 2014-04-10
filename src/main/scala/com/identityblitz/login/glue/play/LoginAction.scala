package com.identityblitz.login.glue.play

import play.api.mvc.{Action, Controller}

object LoginController extends Controller {

  def login = Action {

    Ok("OK!")
  }

}
