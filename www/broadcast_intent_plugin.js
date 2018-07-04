var cordova = require('cordova');
var exec = require('cordova/exec');

var BroadcastIntentPlugin = function() {

  this.scan = function(success_cb, error_cb){
    exec(success_cb, error_cb, "BroadcastIntentPlugin", "scan", []);
  };

  this.listen = function(success_cb, error_cb){
    exec(success_cb, error_cb, "BroadcastIntentPlugin", "listen", []);
  };
};

var broadcastIntentPlugin = new BroadcastIntentPlugin();
module.exports = broadcastIntentPlugin;
