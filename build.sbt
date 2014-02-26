name := "jmsScala"

version := "0.80"

scalaVersion := "2.10.3"

fullResolvers := {
  ("JBoss" at "https://repository.jboss.org/nexus/content/groups/public") +: fullResolvers.value
}

libraryDependencies += "javax.jms" % "jms" % "1.1"

scalaSource in Compile := baseDirectory.value / "src"

excludeFilter in unmanagedSources := "example"
