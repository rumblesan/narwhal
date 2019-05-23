
NarwhalLogger {

  var debuggingOn;

  init { |debug = false|
    debuggingOn = debug;
    this.debug("Debugging is on");
  }

  log { |message|
    "Narwhal ->  %".format(message).postln;
  }

  debug { |message|
    if (debuggingOn, {
      "DEBUG   ->  %".format(message).postln;
    });
  }

  error { |message|
    "Error!  ->  %".format(message).postln;
  }
}
