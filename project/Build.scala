import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "dataportal"
  val appVersion      = "1.0"

  val appDependencies = Seq(
    // Add your project dependencies here,
    //jdbc,
    //anorm,
    //"postgresql" % "postgresql" % "9.1-903.jdbc4",
    "org.hibernate"   % "hibernate-entitymanager" % "4.2.2.Final",
    //"org.hibernate" % "hibernate-core" % "4.2.1.Final",
    //"org.hibernate" % "hibernate-commons-annotations" % "3.2.0.Final",
    "com.google.code.gson"  % "gson"  % "2.2.4",
    "com.typesafe.slick" %% "slick" % "1.0.1"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
