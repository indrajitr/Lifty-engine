version := "0.7"

name := "lifty-engine"

organization := "org.lifty"

scalaVersion := "2.9.1"

publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / "dev" / ".nexus_credentials")

resolvers += "Scala Tools Releases" at "http://scala-tools.org/repo-releases/"

libraryDependencies += "org.scalatest" % "scalatest" % "1.3" % "test"

libraryDependencies += "net.liftweb" %% "lift-json" % "2.4-M5"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.3"

libraryDependencies += "jline" % "jline" % "0.9.94"

libraryDependencies += "org.fusesource.scalate" % "scalate-core" % "1.5.3"

pomPostProcess := {
    import xml._
    Rewrite.rewriter {
        case e: Elem if e.label == "classifier" => NodeSeq.Empty
    }
}