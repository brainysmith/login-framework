package com.identityblitz.login.glue.play

import play.api.mvc._
import com.identityblitz.scs.glue.play.{SCSRequest, SCSEnabledAction}


/**
 */

object LogoutAction {

  def run[A](bodyParser: BodyParser[A])(buildHeader: Request[A] => RequestHeader) ={
    SCSEnabledAction.async(bodyParser) { implicit req =>
      APController.doGet("logout").apply(
        SCSRequest(req.getSCS, buildHeader(req), req.body)
      ).run
    }
  }

  def apply[A](bodyParser: BodyParser[A])(builder: Request[A] => LogoutRequest) = run[A](bodyParser){ req =>
    req.copy(tags = req.tags ++ builder(req).toMap)
  }

  def apply(builder: Request[AnyContent] => LogoutRequest): Action[AnyContent] = apply(BodyParsers.parse.anyContent)(builder)

}

