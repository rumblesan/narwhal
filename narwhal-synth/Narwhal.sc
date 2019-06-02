
Narwhal {

  var logger;
  var synths;
  var fx;

  var tonic;
  var defaultOctave;
  var scale;
  var paramMap, scaleFuncs;
  var fxParamMap, fxScaleFuncs;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths;
    tonic = 36;
    defaultOctave = 2;
    scale = Scale.minor;
  }

  setup { | oscInPort, voices = 4 |
    this.setupAudio(voices);
    this.setupParamMap();
    this.setupFXParamMap();
    this.setupOSC(oscInPort);
  }

  actionSynth{| synthNumber, action |
    if (synths[synthNumber].notNil, {
      action.value(synths[synthNumber]);
    }, {
      logger.error("No synth numbered %".format(synthNumber));
    });
  }

  setupOSC { | oscPort |
    logger.log("Setting up OSC listeners");

    OSCFunc({ | msg |
      var voice = msg[1];
      var note = msg[2];
      var octave = msg[3];
      if (voice.notNil && note.notNil, {
        if (octave.isNil, { octave = defaultOctave });
        this.playSynth(voice, note, octave);
      }, {
        logger.error("Invalid note message arguments: % - %".format(voice, note));
      })
    }, '/n', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var voice = msg[1];
      var param = msg[2];
      var value = msg[3];
      if (param.notNil && value.notNil, {
        this.setSynthParam(voice, param, value);
      }, {
        logger.error("Invalid parameter message arguments");
      });
    }, '/p', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var param = msg[1];
      var value = msg[2];
      if (param.notNil && value.notNil, {
        this.setFXParam(param, value);
      }, {
        logger.error("Invalid fx message arguments");
      });
    }, '/f', NetAddr("localhost"), oscPort);

  }

  setupParamMap {
    paramMap = Dictionary.new;
    scaleFuncs = Dictionary.new;

    paramMap.put(0, \wave);
    scaleFuncs.put(\wave, { arg v; v.clip(0, 1);});

    paramMap.put(1, \cutoff);
    scaleFuncs.put(\cutoff, { arg v; ((v/35) * 3000) + 20;});

    paramMap.put(2, \resonance);
    scaleFuncs.put(\resonance, { arg v; ((v/35) * 3) + 0.1;});

    paramMap.put(3, \sustain);
    scaleFuncs.put(\sustain, { arg v; ((v/35) * 3) + 0.1;});

    paramMap.put(4, \decay);
    scaleFuncs.put(\decay, { arg v; ((v/35) * 3) + 0.1;});

    paramMap.put(5, \envelope);
    scaleFuncs.put(\envelope, { arg v; (v/35) + 0.1;});

    paramMap.put(6, \volume);
    scaleFuncs.put(\volume, { arg v; (v/35) * 1.1;});
  }

  setupFXParamMap {
    fxParamMap = Dictionary.new;
    fxScaleFuncs = Dictionary.new;

    fxParamMap.put(0, \delay);
    fxScaleFuncs.put(\delay, { arg v; (v/36);});
  }

  playSynth { | n, note, octave |
    this.actionSynth(n, { | synth |
      var freq = scale.degreeToFreq(note, tonic.midicps, octave);
      logger.debug("Playing synth %: %hz".format(n, freq));
      Routine {
        synth.set(\freq, freq);
        synth.set(\gate, 1);
        0.01.wait;
        synth.set(\gate, 0);
      }.play;
    });
  }

  setSynthParam { | n, param, value |
    this.actionSynth(n, { | synth |
      var paramName = paramMap[param];
      var scaledValue = scaleFuncs[paramName].value(value);
      logger.debug("Setting synth % % to %".format(n, paramName, scaledValue));
      synth.set(paramName, scaledValue);
    });
  }

  setFXParam { | param, value |
    var paramName = fxParamMap[param];
    var scaledValue = fxScaleFuncs[paramName].value(value);
    logger.debug("Setting fx % to %".format(paramName, scaledValue));
    fx.set(paramName, scaledValue);
  }

  defineSynths {
    logger.log("Defining synths");

    SynthDef(\narwhalFX, {
      arg in, out=0, delay=0.25;
      var signal = In.ar(in);

      var delayChain = DelayN.ar(signal, 2, delay, 1, mul: 0.3);
      var reverb = GVerb.ar(delayChain, 100, 1, mul: 0.3);

      Out.ar(out, reverb + delayChain + signal);
    }).add;

    // copied from https://sccode.org/1-4Wy
    SynthDef(\narwhalSynth, {
      arg out=0, freq=440, wave=0, cutoff=100, resonance=0.2,
          sustain=0, decay=1.0, envelope=1000, gate=0, volume=0.2;
      var filEnv, volEnv, waves;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sustain, decay], 'exp'),
        gate);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, decay], 'exp'),
        gate);
      waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];

      Out.ar(out, RLPF.ar(
        Select.ar(wave, waves),
        cutoff + (filEnv * envelope),
        resonance
      ).dup * volume);
    }).add;

  }

  setupAudio { | voiceCount |
    logger.log("Setting up audio");

    fx = Synth(\narwhalFX, [\in, 2, \out, [0, 1]]);

    synths = voiceCount.collect { | c |
      Synth(\narwhalSynth, [\out, 2]);
    };
  }

}

