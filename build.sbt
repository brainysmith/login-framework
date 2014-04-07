import de.johoop.jacoco4sbt._
import JacocoPlugin._
 
name := "login-framework"

organization := "com.identityblitz"

version := "0.1.0"

licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

homepage := Some(url("https://github.com/brainysmith/conf-lib"))

scalaVersion := "2.10.3"

crossPaths := false

publishMavenStyle := true

publishArtifact in Test := false

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + "/.m2/repository"

libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1",
  /*"com.identityblitz" % "json-lib" % "0.1.0",*/
 /* "com.identityblitz" % "scs-lib" % "0.1.0",*/
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,   //for macros
  "org.scalatest" % "scalatest_2.10" % "2.0.1-SNAP" % "test,it",
  "org.scalacheck" %% "scalacheck" % "1.11.2" % "test,it"
)

scalacOptions ++= List("-feature","-deprecation", "-unchecked")

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")

//Code Coverage section
jacoco.settings

//itJacoco.settings

//Style Check section 
org.scalastyle.sbt.ScalastylePlugin.Settings
 
org.scalastyle.sbt.PluginKeys.config <<= baseDirectory { _ / "src/main/config" / "scalastyle-config.xml" }
