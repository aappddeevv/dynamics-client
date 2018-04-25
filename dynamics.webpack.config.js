let webpack = require('webpack'),
    merge = require('webpack-merge'),
    nodeExternals = require('webpack-node-externals'),
    UglifyJsPlugin = require("uglifyjs-webpack-plugin"),
    fs = require("fs-extra"),
    path = require("path"),
    util = require("util"),
    common, config

const banner =
    "#!/usr/bin/env node\n" +
    "try { require(\"source-map-support\").install(); } catch (e) {}"

const cli = "dynamicscli",
    outfiledir = path.resolve(__dirname, "bin"),
    outfile = path.resolve(outfiledir, cli)

common = {
    output: {
        path: outfiledir,
        filename: cli,
        pathinfo: true
    },
    target: "node",
    devtool: "source-map",
    // do not bundle any node_modules, no monolithic output
    externals: [nodeExternals()],
    resolve: {
        alias: {
            Provided: path.resolve(__dirname, "./cli/src/main/resources"),
            app: path.resolve(__dirname, "./cli/src/main/scala/dynamics/cli"),
            JS: path.resolve(__dirname, "./cli/src/main/js")
        },
        // we can place absolute referenced packages here
        modules: [path.resolve(__dirname, "./cli/src/main/resources"), "node_modules"]
    },
    module: {
        rules: [
            {
                test: require.resolve("winston"),
                use: "imports-loader?this=>global"
            },
            {
                test: /\.js$/,
                use: [{
                    loader: "scalajs-friendly-source-map-loader",
                    options: {
                      // skipFileURLWarnings: true
                    }
                }],
                enforce: "pre",
                exclude: [/node_modules/],

            }
        ]
    },
    plugins: [
        new webpack.BannerPlugin({
            banner: banner,
            raw: true
        }),
        function() {
            // change mode to executable on linux
            this.plugin('done', () => {
                fs.chmodSync(outfile, "755")
            })
        }
    ],
}

const prod = {
    entry: path.resolve(__dirname, "cli-main/target/scala-2.12/dynamics-client-cli-main-opt.js"),
    plugins: [
        new webpack.DefinePlugin({
            "proces.env": {
                "NODE_ENV": '"production"'
            }
        }),
        new UglifyJsPlugin({
            parallel: 4,
            cache: true,
            sourceMap: "inline",
        })
    ]
}

const dev = {
    entry: path.resolve(__dirname, "cli-main/target/scala-2.12/dynamics-client-cli-main-fastopt.js")
}

switch (process.env.npm_lifecycle_event) {
case 'afterScalaJSFast':
    config = merge(common, dev)
    break
    
case 'afterScalaJSFull':
    config = merge(common, prod)
    break

default:
    console.log("No npm_lifecyle_event specified. Using dev config")
    config = merge(common, dev)
    break
}

module.exports = function(env) {
    return config
}
