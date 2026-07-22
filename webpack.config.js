const webpack = require("webpack");
const fs = require("fs");
const path = require("path");

// scala-js-bundler copies this config next to the generated bundle before
// loading it, so find the source checkout in both the original and copied use.
const projectRoot = fs.existsSync(path.resolve(__dirname, "build.sbt"))
  ? __dirname
  : path.resolve(__dirname, "../../../..");

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
    alias: {
      "hafas-db-base-profile": require.resolve("hafas-client/p/db/base.json")
    },
    fallback: {
      assert: require.resolve("assert/"),
      buffer: require.resolve("buffer/"),
      crypto: require.resolve("crypto-browserify"),
      http: require.resolve("stream-http"),
      https: require.resolve("https-browserify"),
      // hafas-client's DB profile uses Node's createRequire solely to load its
      // base.json. Replace that API with the small browser-safe adapter below.
      module: path.resolve(projectRoot, "webpack/module-browser.js"),
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
