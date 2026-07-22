sbt compile
sbt fullOptJS
echo("server starting")
python -m http.server
echo("server shut down!")