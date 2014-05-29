package com.identityblitz.login

/**
 */
trait FlowTools {

  private val extractor  = """^fwd:(.*)$""".r

  protected val crackCallbackUrl = (s: String) => {
    extractor findFirstIn s match {
      case Some(extractor(url)) => ("forward", url)
      case _ => ("redirect", s)
    }
  }

}
