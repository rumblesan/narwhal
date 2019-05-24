
Narwhal {

  var logger;
  var synths;
  var fx;

  var tonic;
  var defaultOctave;
  var scale;
  var paramMap;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths;
    tonic = 36;
    defaultOctave = 2;
    scale = Scale.minor;
  }

  setup { | oscInPort, voices = 4 |
    this.setupAudio(voices);
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

    OSCFunc({ |msg|
      this.playSynth(msg[1], msg[2], msg[3]);
    }, '/n', NetAddr("localhost"), oscPort);

    OSCFunc({ |msg|
      this.setSynthParam(msg[1], msg[2], msg[3]);
    }, '/p', NetAddr("localhost"), oscPort);

  }

  setupParamMap {
    paramMap = Dictionary.new;
    paramMap.put(0, \wave);
    paramMap.put(1, \cutoff);
    paramMap.put(2, \resonance);
    paramMap.put(3, \sustain);
    paramMap.put(4, \decay);
    paramMap.put(5, \envelope);
    paramMap.put(6, \volume);
  }

  playSynth { | n, note, octave |
    this.actionSynth(n, { | synth |
      if (note.isNil, {
        logger.error("No note given");
        ^n;
      });
      if (octave.isNil {
        octave = defaultOctave;
      });
      logger.debug("Playing synth %".format(n));
      Routine {
        synth.set(\freq, scale.degreeToFreq(note, tonic.midicps, octave));
        synth.set(\gate, 1);
        0.01.wait;
        synth.set(\gate, 0);
      }.play;
    });
  }

  setSynthParam { | n, param, value |
    this.actionSynth(n, { | synth |
      if (param.isNil || value.isNil, {
        logger.error("No param name or value given");
        ^n;
      });
      paramName = paramMap[param];
      synth.set(paramName, value/35);
    });
  }

  defineSynths {
    logger.log("Defining synths");

    SynthDef(\narwhalFX, {
      arg in, out=0;
      Out.ar(out, In.ar(in));
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

    fx = Synth(\narwhalFX, [\in, 1]);

    synths = voiceCount.collect { | c |
      Synth(\narwhalSynth, [\out, 1]);
    };
  }

}

