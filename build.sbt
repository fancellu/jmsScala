name := "jmsScala"

version := "0.82"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.10.3", "2.11.8")

fullResolvers := {
  ("JBoss" at "https://repository.jboss.org/nexus/content/groups/public") +: fullResolvers.value
}

libraryDependencies += "javax.jms" % "jms" % "1.1"

scalaSource in Compile := baseDirectory.value / "src"

excludeFilter in unmanagedSources := "example"
