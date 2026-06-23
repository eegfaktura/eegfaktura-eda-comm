import com.typesafe.sbt.packager.docker.*
import scalapb.GeneratorOption.*

ThisBuild / version := "0.2.2"

ThisBuild / scalaVersion := "2.13.9"

val appVersion      = "v0.2.2"

lazy val emilVersion     = "0.12.0"
lazy val akkaHttpVersion = "10.2.6"
//lazy val akkaVersion     = "2.6.19"
lazy val akkaVersion     = "2.7.0"
lazy val courierVersion  = "3.0.1"
lazy val alpakkaVersion  = "4.0.0"
lazy val circeVersion    = "0.14.3"
lazy val slickVersion = "3.4.1"

lazy val root = (project in file("."))
  .enablePlugins(ScalaxbPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(dockerSettings)
  .settings(
    name := "EmailClient",

    organization := "vfeeg-development",

    idePackagePrefix := Some("at.energydash"),

    resolvers += "repo.jenkins-ci.org" at "https://repo.jenkins-ci.org/releases",

    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),

//    akkaGrpcCodeGeneratorSettings / target := crossTarget.value / "src_managed1" /*/ Defaults.nameForSrc(configuration.value.name)*/,
//
//    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package"),

    libraryDependencies ++= Seq(
      "com.github.daddykotex" %% "courier" % courierVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.37.0",
//      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % alpakkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % alpakkaVersion,
//      "com.enragedginger" %% "akka-quartz-scheduler" % "1.9.3-akka-2.6.x",

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      //      "io.spray" %% "spray-json" % "1.3.6",

      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

//      "org.scalactic" %% "scalactic" % "3.0.8",
//      "org.scalatest" %% "scalatest" % "3.2.14" % Test,
//      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      "javax.xml.bind" % "jaxb-api" % "2.3.0",
      "com.sun.xml.bind" % "jaxb-core" % "2.3.0",
      "org.scala-lang.modules" %% "scala-xml" % "2.0.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",

      "io.crums" % "merkle-tree" % "1.0.0",
      "com.google.guava" % "guava" % "31.1-jre"
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.14",
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
      "org.jvnet.mock-javamail" % "mock-javamail" % "1.12",
      "com.typesafe.slick" %% "slick-testkit" % slickVersion,
    ).map(_ % Test),

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),

    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "org.postgresql" % "postgresql" % "42.2.5"
    ),

//    Compile / PB.targets := Seq(
//      scalapb.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
//    ),

//    Compile / akkaGrpcCodeGeneratorSettings / target := crossTarget.value / "src_managed" / "main" / "akka-grpc",

    Compile / scalaxb / scalaxbPackageName := "xmlprotocol",
    Compile / scalaxb / scalaxbProtocolPackageName := Some("xmlprotocol"),

    // Vollständiges Per-Namespace-Mapping: jede versionierte XSD landet in
    // ihrem eigenen Sub-Package. Im Single-Package kollidieren gleichnamige
    // Typen aus verschiedenen Schema-Versionen (CMRevoke, MarketParticipant-
    // Directory, …) und scalaxb vergibt instabile Auto-Suffixe (…Type6,
    // …Value4). Mit der Isolation erhält jedes Sub-Package saubere Namen und
    // eine neue Schema-Version (hier cmrevoke/01p10) lässt sich kollisionsfrei
    // ergänzen. CPCommonTypes_01p20 bleibt im Default-Package "xmlprotocol",
    // weil es die von allen XSDs gemeinsam genutzten Basis-Typen (RoutingHeader,
    // AddressType, Sector, …) bereitstellt.
    Compile / scalaxb / scalaxbPackageNames := Map(
      new java.net.URI("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10")
        -> "xmlprotocol.cmrequest.v01p10",
      new java.net.URI("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20")
        -> "xmlprotocol.cmrequest.v01p20",
      new java.net.URI("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00")
        -> "xmlprotocol.cmrevoke.v01p00",
      new java.net.URI("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p10")
        -> "xmlprotocol.cmrevoke.v01p10",
      new java.net.URI("http://www.ebutilities.at/schemata/customerconsent/cmnotification/01p11")
        -> "xmlprotocol.cmnotification.v01p11",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12")
        -> "xmlprotocol.cprequest.v01p12",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/cpnotification/01p13")
        -> "xmlprotocol.cpnotification.v01p13",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/cpdocument/01p12")
        -> "xmlprotocol.cpdocument.v01p12",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/consumptionrecord/01p30")
        -> "xmlprotocol.consumptionrecord.v01p30",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/consumptionrecord/01p31")
        -> "xmlprotocol.consumptionrecord.v01p31",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/consumptionrecord/01p40")
        -> "xmlprotocol.consumptionrecord.v01p40",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p00")
        -> "xmlprotocol.ecmplist.v01p00",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10")
        -> "xmlprotocol.ecmplist.v01p10",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequest/01p00")
        -> "xmlprotocol.gc.gcrequest.v01p00",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00")
        -> "xmlprotocol.gc.gcrequestap.v01p00",
      new java.net.URI("http://www.ebutilities.at/schemata/customerprocesses/gc/gcresponseap/01p00")
        -> "xmlprotocol.gc.gcresponseap.v01p00"
    ),

    Compile / managedSourceDirectories ++= Seq(
      target.value / "scala-2.13" / "akka-grpc" / "main",
      target.value / "scala-2.13" / "src_managed" / "main"
    ),

    Test / javaOptions ++= Seq(
      s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf",
      // Date-Felder (z.B. CMRevoke ConsentEnd) werden via SimpleDateFormat in
      // der Default-TZ gerendert; Tests asserten Wiener Datums-Strings. Ohne
      // feste TZ rendert ein UTC-CI einen Tag früher.
      "-Duser.timezone=Europe/Vienna"
    ),

//    Test / resourceDirectory := baseDirectory.value / "test-resources",

    Test / fork := true,


  )

lazy val dockerSettings = Seq(
  Docker / packageName := "eda-email-connector",
  Docker / maintainer := "vfeeg <vfeeg.org>",
  Docker / version := appVersion,

    //  Docker / dockerPackageMappings += (baseDirectory.value / "src" / "universal" / "application-app.conf") -> "application-app.conf",
  dockerBaseImage := "openjdk:17-slim-buster",
//  dockerExposedPorts := Seq(9000),
//  Docker / daemonUserUid := None,
//  Docker / daemonUser := "daemon",
  dockerExposedVolumes := Seq("/conf"),
  dockerExposedVolumes := Seq("/storage/prod"),
  dockerRepository := Some("ghcr.io"),
  dockerUsername := Some("vfeeg-development"),
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(8880, 9090),
  dockerCommands := dockerCommands.value.filterNot {
    case ExecCmd("ENTRYPOINT", _) => true
    case cmd => false
  },
  dockerCommands ++= Seq(
//    Cmd("ADD", "application-app.conf", "/conf/application.conf"),
    Cmd("LABEL", s"""version="${appVersion}""""),
    ExecCmd("CMD", "/opt/docker/bin/emailclient", "-Dconfig.file=/conf/application.conf")
  ),
  dockerChmodType := DockerChmodType.UserGroupWriteExecute
)
