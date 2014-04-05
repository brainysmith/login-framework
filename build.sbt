import de.johoop.jacoco4sbt._
import JacocoPlugin._
 
name := "login-framework"

organization := "com.identityblitz"

version := "0.1.0"

scalaVersion := "2.10.3"

crossPaths := false

libraryDependencies ++= Seq(
  "com.identityblitz" % "json-lib" % "0.1.0",
 /* "com.identityblitz" % "scs-lib" % "0.1.0",*/
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,   //for macros
  "org.scalatest" % "scalatest_2.10" % "2.0.1-SNAP" % "test,it",
  "org.scalacheck" %% "scalacheck" % "1.11.2" % "test,it"
)

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + "/.m2/repository"

scalacOptions ++= List("-feature","-deprecation", "-unchecked")

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")

//Code Coverage section
jacoco.settings

//itJacoco.settings

//Style Check section 
org.scalastyle.sbt.ScalastylePlugin.Settings
 
org.scalastyle.sbt.PluginKeys.config <<= baseDirectory { _ / "src/main/config" / "scalastyle-config.xml" }
