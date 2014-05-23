package com.identityblitz.login

import com.identityblitz.login.provider.Provider
import com.identityblitz.login.provider.flow.{DefaultLoginFlowProvider, LoginFlowProvider}
import App.logger

case class LoginFlow(provider: LoginFlowProvider)

object LoginFlow {

  private def defaultLFP = Provider("default-login-flow", Map("class" -> classOf[DefaultLoginFlowProvider].getName))

  def apply(options: Map[String, String], providers: Map[String, Provider]): LoginFlow = apply(options.get("provider")
    .flatMap(providers.get)
    .filter(p => {classOf[LoginFlowProvider].isAssignableFrom(p.getClass)})
    .orElse{
      logger.warn("Will use the default login flow provider because:  1) it's not specified; " +
        "2) specified wrong name; 3) specified provider is't LoginFlowProvider.")
      Option(defaultLFP)}
    .map{ p =>
      logger.debug("Will use the following login flow provider: {}", p.name)
      p.asInstanceOf[LoginFlowProvider]
    }
    .get)

}
