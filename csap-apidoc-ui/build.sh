rm -rf src/main/resources/static
echo "rm -rf src/main/resources/static success"
npm run build
mv dist/index.html dist/csap-api.html
cp -rf dist src/main/resources/static
mvn clean install
