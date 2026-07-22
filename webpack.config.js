const webpack = require("webpack");
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
  devtool: "source-map",
  resolve: {
    fallback: {
      assert: require.resolve("assert/"),
      buffer: require.resolve("buffer/"),
      crypto: require.resolve("crypto-browserify"),
      http: require.resolve("stream-http"),
      https: require.resolve("https-browserify"),
      module: false,
      net: false,
      process: require.resolve("process/browser.js"),
      stream: require.resolve("stream-browserify"),
      tls: false,
      vm: false
    }
  },
  plugins: [
    new webpack.ProvidePlugin({
      Buffer: ["buffer", "Buffer"],
      process: "process/browser.js"
    })
  ]
};
