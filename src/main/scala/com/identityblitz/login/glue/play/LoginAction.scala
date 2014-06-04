package com.identityblitz.login.glue.play

import play.api.mvc._
import com.identityblitz.scs.glue.play.{SCSRequest, SCSEnabledAction}


/**
 */

object LoginAction {

  def run[A](bodyParser: BodyParser[A])(buildHeader: Request[A] => RequestHeader) ={
    SCSEnabledAction.async(bodyParser) { implicit req =>
      APController.doGet("flow").apply(
        SCSRequest(req.getSCS, buildHeader(req), req.body)
      ).run
    }
  }

  def apply[A](bodyParser: BodyParser[A])(buildLr: Request[A] => LoginRequest) = run[A](bodyParser){ req =>
    req.copy(tags = req.tags ++ buildLr(req).toMap)
  }

  def apply(buildLr: Request[AnyContent] => LoginRequest): Action[AnyContent] = apply(BodyParsers.parse.anyContent)(buildLr)

}

