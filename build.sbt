name := "UserlogAnalysis_UDF"

version := "0.1"

scalaVersion := "2.12.10"

scapegoatVersion in ThisBuild := "1.3.8"

coverageEnabled := true

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.1" % "provided"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.1" % "provided"
