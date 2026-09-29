echo "fetch and pull"
git fetch
git pull 

sbt deployAll

echo "Server starting on http://localhost:${PORT:-8000}"
python3 -m http.server "${PORT:-8000}" --directory target/deploy

