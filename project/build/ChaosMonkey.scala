import sbt._

class ChaosMonkeyProject(info: ProjectInfo) extends DefaultProject(info)
{
  val aws = "com.amazonaws" % "aws-java-sdk" % "1.1.9"
  val scalatime = "org.scala-tools.time" %% "time" % "0.3"
  // val log4j = "log4j" % "log4j" % "1.2.9"
  // Logula
  val codaRepo = "Coda Hale's Repository" at "http://repo.codahale.com/"
  val logula = "com.codahale" %% "logula" % "2.1.1" withSources()
  
  override def mainClass = Some("com.bubblefoundry.chaosmonkey.ReleaseTheMonkey")
}