var exec = require('cordova/exec');

var BroadcastIntentPlugin = function () {};

/**
 * Constants for checking states
 */

BroadcastIntentPlugin.prototype.STATE_NONE = 0;       // we're doing nothing
BroadcastIntentPlugin.prototype.STATE_READY = 1; // BCR reader ready
BroadcastIntentPlugin.prototype.STATE_READING = 2; // reading BCR reader
BroadcastIntentPlugin.prototype.STATE_READ = 3; /// read received BCR reader
BroadcastIntentPlugin.prototype.STATE_ERROR = 4; // error
BroadcastIntentPlugin.prototype.STATE_DESTROYED = 5; // BCR reader destroyed

BroadcastIntentPlugin.prototype.init = function (success_cb, error_cb, profileName) {
  exec(success_cb, error_cb, "BroadcastIntentPlugin", "init", [profileName]);
};

BroadcastIntentPlugin.prototype.destroy = function (success_cb, error_cb) {
  exec(success_cb, error_cb, "BroadcastIntentPlugin", "destroy", []);
};

BroadcastIntentPlugin.prototype.listen = function (success_cb, error_cb) {
  exec(success_cb, error_cb, "BroadcastIntentPlugin", "listen", []);
};

BroadcastIntentPlugin.prototype.getState = function (success_cb, error_cb) {
  exec(success_cb, error_cb, "BroadcastIntentPlugin", "getState", []);
};

var broadcastIntentPlugin = new BroadcastIntentPlugin();
module.exports = broadcastIntentPlugin;