const path = require("path");

module.exports = {
  mode: "development",
  entry: {
    "bus-homepage-fastopt": path.resolve(__dirname, "bus-homepage-fastopt.js")
  },
  output: {
    path: __dirname,
    filename: "[name]-bundle.js"
  },
  devtool: "source-map"
};
