import de.johoop.jacoco4sbt._
import JacocoPlugin._
 
name := "login-framework"

organization := "com.identityblitz"

version := "0.3.0-SNAPSHOT"

licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

homepage := Some(url("https://github.com/brainysmith/login-framework"))

scalaVersion := "2.10.3"

crossPaths := false

publishMavenStyle := true

publishArtifact in Test := false

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + "/.m2/repository"

resolvers += "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases"

resolvers += "Reaxoft Public Repository" at "http://build.reaxoft.loc/store/content/groups/public"

val reaxoft_local_repository = "http://build.reaxoft.loc/store/content/repositories"
val maven_central = "https://oss.sonatype.org/"

credentials += Credentials("Sonatype Nexus Repository Manager", "build.reaxoft.loc", "deployment", "oracle_1")

publishTo <<= version { (v: String) =>
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at reaxoft_local_repository + "/blitz-snapshots")
  else
    //Some("reaxoft-releases" at reaxoft_local_repository + "/blitz-releases")
    Some("maven-central" at maven_central + "service/local/staging/deploy/maven2")
}

libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.0.1",
  "org.slf4j" % "slf4j-api" % "1.6.6",
  "commons-codec" % "commons-codec" % "1.9",
  "commons-lang" % "commons-lang" % "2.6",
  "com.identityblitz" % "json-lib" % "0.1.0",
  "com.identityblitz" % "scs-lib" % "0.2.0",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,   //for macros
  "org.scalatest" % "scalatest_2.10" % "2.0.1-SNAP" % "test,it",
  "org.scalacheck" %% "scalacheck" % "1.11.2" % "test,it",
  "com.typesafe.play" % "play_2.10" % "2.3.4" % "provided" exclude("org.slf4j", "slf4j-api"),
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.4",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.48"
)

scalacOptions ++= List("-feature","-deprecation", "-unchecked")

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")

//Code Coverage section
jacoco.settings

//itJacoco.settings

//Style Check section 
//org.scalastyle.sbt.ScalastylePlugin.Settings
 
//org.scalastyle.sbt.PluginKeys.config <<= baseDirectory { _ / "src/main/config" / "scalastyle-config.xml" }

pomExtra := (
    <developers>
        <developer>
            <name>Vladimir Karpov</name>
            <email>brainysmith@gmail.com</email>
        </developer>
    </developers>
    <scm>
        <connection>scm:git:git@github.com:brainysmith/log-lib.git</connection>
        <developerConnection>scm:git:git@github.com:brainysmith/log-lib.git</developerConnection>
        <url>git@github.com:brainysmith/log-lib.git</url>
    </scm>
  )
