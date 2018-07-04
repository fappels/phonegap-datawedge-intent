var cordova = require('cordova');
var exec = require('cordova/exec');

var BroadcastIntentPlugin = function() {

  this.startListen = function(success_cb, error_cb){
    exec(success_cb, error_cb, "BroadcastIntentPlugin", "scan", []);
  };

};

var broadcastIntentPlugin = new BroadcastIntentPlugin();
module.exports = broadcastIntentPlugin;
