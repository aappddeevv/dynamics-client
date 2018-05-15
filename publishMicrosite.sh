#!/bin/env sh

#SCALAJS_VERSION="1.0.0-M3" sbt clean fullOptJS
#sbt clean fullOptJS
sbt fullOptJS publishMicrosite
#sbt publishMicrosite
