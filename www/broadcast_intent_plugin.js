var exec = require('cordova/exec');

var BroadcastIntentPlugin = function () {

  this.destroy = function (success_cb, error_cb) {
    exec(success_cb, error_cb, "BroadcastIntentPlugin", "destroy", []);
  };

  this.listen = function (success_cb, error_cb) {
    exec(success_cb, error_cb, "BroadcastIntentPlugin", "listen", []);
  };
};

var broadcastIntentPlugin = new BroadcastIntentPlugin();
module.exports = broadcastIntentPlugin;